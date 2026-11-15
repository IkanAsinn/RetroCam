package com.retrocam.capture

import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import com.retrocam.camera.CapturedFrame
import com.retrocam.camera.PhotoSaver
import com.retrocam.data.Settings
import com.retrocam.filmengine.BitmapMirror
import com.retrocam.filmengine.DateStampRenderer
import com.retrocam.filmengine.FrameComposer
import com.retrocam.filmengine.FrameFormat
import com.retrocam.filmengine.FlashAnalyzer
import com.retrocam.filmengine.Highlight
import com.retrocam.filmengine.IllumMap
import com.retrocam.filmengine.OffscreenFilmRenderer
import com.retrocam.logic.FrameSelection
import com.retrocam.logic.LensMapping
import com.retrocam.logic.MirrorLogic
import com.retrocam.logic.ReleasableHolder
import com.retrocam.logic.StrengthMath
import com.retrocam.logic.UiMath
import com.retrocam.profiles.CameraProfile
import com.retrocam.profiles.ParamsMapper
import com.retrocam.profiles.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random

/** Everything about one shot that was true when the shutter fired. */
class ShotRequest(
    val frame: CapturedFrame,
    val profile: CameraProfile,
    val settings: Settings,
    val highlight: Highlight?,
    val location: Location?,
)

/**
 * capture -> ParamsMapper -> OffscreenFilmRenderer -> FrameComposer -> DateStampRenderer -> PhotoSaver.
 * Runs entirely off the main thread and always recycles its bitmaps. Throws on failure; the caller reports it.
 */
class CapturePipeline(
    private val profiles: ProfileRepository,
    private val renderer: ReleasableHolder<OffscreenFilmRenderer>,
    private val saver: PhotoSaver,
    private val random: Random = Random.Default,
) {
    suspend fun process(shot: ShotRequest): Uri = withContext(Dispatchers.Default) {
        val frame = shot.frame
        val toRecycle: MutableSet<Bitmap> = Collections.newSetFromMap(IdentityHashMap())
        toRecycle += frame.bitmap
        try {
            val lut = profiles.lut(shot.profile.id)
            var params = ParamsMapper.toFilmParams(
                profile = shot.profile,
                toggles = shot.settings.toggles,
                lens = LensMapping.toLensKind(frame.lens),
                flashActive = frame.flashFired,
                seed = random.nextInt(), // new grain pattern, leak and colour drift every shot
                brightHighlight = shot.highlight != null,
                highlightX = shot.highlight?.x,
                highlightY = shot.highlight?.y,
                filmFlash = shot.settings.filmFlash, // independent of the real flash; stacks with it (see ParamsMapper.relightLook)
            ).copy(strength = StrengthMath.toStrength(shot.settings.strengthFor(shot.profile.id))) // same strength as the preview
            // Film flash: look for faces and the ambient light on a small copy; any failure just skips the simulated flash.
            var illum: IllumMap? = null
            val look = params.relight
            if (look != null) {
                val analysis = FlashAnalyzer.analyze(frame.bitmap)
                if (analysis != null) {
                    illum = analysis.illum
                    params = params.copy(relight = look.copy(subject = analysis.subject, ambientLuma = analysis.ambientLuma))
                } else {
                    params = params.copy(relight = null)
                }
            }
            val rendered = renderer.use { it.render(frame.bitmap, params, lut, illum) }
            toRecycle += rendered
            if (rendered !== frame.bitmap) frame.bitmap.recycle() // free the 48 MB input early

            // Mirror after the film render (leaks/flare already match the mirrored preview) and before the frame crop and
            // the date stamp (the stamp text must never be mirrored). horizontal() recycles its input.
            val filmed = if (MirrorLogic.shouldMirror(shot.settings.mirrorFront, frame.facing)) {
                BitmapMirror.horizontal(rendered).also { toRecycle += it }
            } else {
                rendered
            }

            val format = FrameSelection.choose(shot.profile.frame, shot.settings.frameOption)
            var finished = FrameComposer.apply(filmed, format)
            if (format == FrameFormat.FULL) {
                // User ratio: crop the finished render (centre, orientation-aware) before the date stamp so the stamp sits in the final corner.
                finished = FrameComposer.applyRatio(finished, shot.settings.ratio)
            }
            toRecycle += finished

            if (shot.settings.dateStamp) {
                if (!finished.isMutable) {
                    finished = finished.copy(Bitmap.Config.ARGB_8888, true)
                    toRecycle += finished
                }
                val date = Instant.ofEpochMilli(frame.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                DateStampRenderer.draw(
                    finished,
                    DateStampRenderer.text(date, shot.settings.dateFormat),
                    UiMath.hexToRgb(shot.profile.dateStampColor),
                )
            }
            saver.save(finished, frame.timestampMillis, shot.location)
        } finally {
            toRecycle.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
