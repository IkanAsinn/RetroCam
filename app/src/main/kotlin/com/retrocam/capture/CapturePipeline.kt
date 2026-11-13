package com.retrocam.capture

import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import com.retrocam.camera.CapturedFrame
import com.retrocam.camera.PhotoSaver
import com.retrocam.data.Settings
import com.retrocam.filmengine.DateStampRenderer
import com.retrocam.filmengine.FrameComposer
import com.retrocam.filmengine.OffscreenFilmRenderer
import com.retrocam.logic.FrameSelection
import com.retrocam.logic.LensMapping
import com.retrocam.logic.ReleasableHolder
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
    val brightHighlight: Boolean,
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
            val params = ParamsMapper.toFilmParams(
                profile = shot.profile,
                toggles = shot.settings.toggles,
                lens = LensMapping.toLensKind(frame.lens),
                flashActive = frame.flashFired,
                seed = random.nextInt(), // new grain pattern, leak and colour drift every shot
                brightHighlight = shot.brightHighlight,
            )
            val rendered = renderer.use { it.render(frame.bitmap, params, lut) }
            toRecycle += rendered
            if (rendered !== frame.bitmap) frame.bitmap.recycle() // free the 48 MB input early

            val format = FrameSelection.choose(shot.profile.frame, shot.settings.frameOption)
            var finished = FrameComposer.apply(rendered, format)
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
