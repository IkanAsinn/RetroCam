# RetroCam architecture and module contracts

This file is the contract between modules. Each module is built by a different
agent in parallel, so **public APIs listed here must not change without the
orchestrator's approval**. Internals are free. Read `docs/PRD.md` for product
requirements.

## Build facts

- Root package `com.retrocam`. Modules: `:app`, `:camera`, `:filmengine`, `:profiles`.
- Dependency graph: `:app → :camera, :filmengine, :profiles`; `:profiles → :filmengine`;
  `:camera` and `:filmengine` depend on no other project module.
- minSdk 28 (Android 9+), targetSdk/compileSdk 36, JDK 21 toolchain, Kotlin 2.x, version catalog
  at `gradle/libs.versions.toml`. Only add libraries through the catalog.
- Local SDK: `export ANDROID_HOME=/opt/android-sdk` (or `local.properties`, gitignored).
- Every change must pass: `./gradlew assembleDebug testDebugUnitTest lintDebug`
  (plus `assembleDebugAndroidTest` for modules with `androidTest`).
- No `INTERNET` permission anywhere, no analytics, no ads, no watermark (PRD hard rules).
- Coroutines for async; manual constructor injection (no Hilt/Koin).

## `:filmengine` — `com.retrocam.filmengine`

Owns the GPU film look, applied identically to preview frames and full-res captures.

```kotlin
// Every value the shader needs. Pure data, no Android types except where noted.
data class FilmParams(
    val exposureEv: Float = 0f,            // -1..+1
    val blackLift: Float = 0f,             // 0..0.15
    val contrast: Float = 1f,              // 0.7..1.4
    val saturation: Float = 1f,            // 0..2, applied after LUT
    val grainAmount: Float = 0f,           // 0..1 (already scaled by user grain level)
    val grainSizePx: Float = 1.5f,         // 1..3, in px at REFERENCE_SHORT_EDGE
    val vignetteStrength: Float = 0f,      // 0..1
    val vignetteSoftness: Float = 0.5f,    // 0..1
    val halationStrength: Float = 0f,      // 0..1
    val halationTint: Rgb = Rgb(1f, 0.2f, 0.1f),
    val chromaticAberrationPx: Float = 0f, // 0..4 at corners, at REFERENCE_SHORT_EDGE
    val edgeSoftness: Float = 0f,          // 0..1
    val flash: FlashLook? = null,          // non-null only when the flash fired (capture) or flash is ON (preview)
    val flare: FlareLook? = null,          // null = no flare
    val leak: LeakLook? = null,            // null = no light leak
    val colorShift: Rgb = Rgb(0f, 0f, 0f), // per-shot random cast, added after LUT (-0.1..0.1)
    val seed: Int = 0,                     // per-shot random seed: grain pattern, leak position, flare
) { companion object { const val REFERENCE_SHORT_EDGE = 1080 } }

data class Rgb(val r: Float, val g: Float, val b: Float)
data class FlashLook(val hotspotSize: Float, val falloff: Float, val kelvin: Float, val strength: Float, val gel: Rgb? = null)
data class FlareLook(val style: FlareStyle, val strength: Float, val centerX: Float, val centerY: Float) // centre in 0..1 UV
enum class FlareStyle { WARM, RAINBOW }
data class LeakLook(val tint: Rgb, val strength: Float, val angle: Float, val offset: Float)

// 33x33x33 3D LUT. PNG layout is 1089x33: pixel (x = b*33 + r, y = g) holds the output
// colour for input (r, g, b) / 32. 8-bit sRGB.
class Lut(val size: Int, val rgb: ByteArray /* size^3 * 3, index ((b*size + g)*size + r)*3 */) {
    fun sample(r: Float, g: Float, b: Float): Rgb   // trilinear, CPU reference (used by tests)
    companion object {
        fun identity(size: Int = 33): Lut
        fun fromStripPixels(width: Int, height: Int, argb: IntArray): Lut  // pure, JVM-testable
        fun fromBitmap(bitmap: android.graphics.Bitmap): Lut
    }
}

// Preview path: a CameraX CameraEffect (target PREVIEW) wrapping a GL SurfaceProcessor.
class FilmCameraEffect(errorListener: (Throwable) -> Unit) : androidx.camera.core.CameraEffect {
    fun update(params: FilmParams, lut: Lut)   // thread-safe, may be called every frame
    // Brightest region of the (unfiltered) preview, sampled every ~10 frames from a small
    // downscaled read-back. Drives FlareMode.AUTO and the flare centre. null = nothing bright.
    val highlight: kotlinx.coroutines.flow.StateFlow<Highlight?>
    fun release()
}
data class Highlight(val x: Float, val y: Float, val luma: Float)  // x, y in 0..1 UV of the upright frame
// Preview grain animates (the effect adds its own frame counter to params.seed); captures use params.seed as-is.

// Capture path: the SAME shader on an EGL pbuffer on its own GL thread. Tiles inputs
// larger than GL_MAX_TEXTURE_SIZE. Output has the input's dimensions.
class OffscreenFilmRenderer {
    suspend fun render(input: android.graphics.Bitmap, params: FilmParams, lut: Lut): android.graphics.Bitmap
    fun release()
}

// Post-processing on the finished Bitmap (capture path only; preview draws these as Compose overlays).
enum class FrameFormat { FULL, SQUARE, INSTANT_SQUARE, INSTANT_MINI, HALF_FRAME }
object FrameComposer { fun apply(src: android.graphics.Bitmap, format: FrameFormat): android.graphics.Bitmap }
enum class DateStampFormat { YY_MM_DD, DD_MM_YY }   // 'YY MM DD and DD MM 'YY
object DateStampRenderer {
    fun text(date: java.time.LocalDate, format: DateStampFormat): String   // pure, e.g. "'26 11 13"
    fun draw(target: android.graphics.Bitmap, text: String, color: Rgb)     // 7-segment, bottom-right, in place
}
```

The GLSL source lives in one place (`FilmShader.kt`) and is used by both paths.
Grain and chromatic aberration scale by `outputShortEdge / REFERENCE_SHORT_EDGE`
so the 12 MP file looks like the preview. Grain is luminance-dependent (stronger in
shadows) and reseeded from `seed`.

## `:profiles` — `com.retrocam.profiles`

Owns camera types: JSON + LUT PNG in `src/main/assets/profiles/<id>.json` and
`src/main/assets/luts/<id>.png`, plus camera-card vector drawables.

```kotlin
enum class CameraFamily { DISPOSABLE, COMPACT, INSTANT, BLACK_AND_WHITE, SLIDE_CINEMA, TOY }
enum class LensKind { ULTRA_WIDE, MAIN, TELE, FRONT }
enum class GrainLevel { OFF, LOW, NORMAL, HIGH }   // multipliers 0, 0.5, 1, 1.6
enum class FlareMode { OFF, AUTO, ALWAYS }
enum class LeakMode { OFF, SUBTLE, STRONG }
enum class FlareStyleSpec { NONE, WARM, RAINBOW, RANDOM }

@Serializable data class CameraProfile(
    val id: String,                 // snake_case, equals file names, e.g. "gold_snap"
    val name: String,               // in-app name, e.g. "Gold Snap" (no trademarks)
    val family: CameraFamily,
    val release: String,            // "1.0" or "1.1"
    val exposureEv: Float, val blackLift: Float, val contrast: Float, val saturation: Float = 1f,
    val grain: GrainSpec, val vignette: VignetteSpec, val halation: HalationSpec,
    val chromaticAberration: Float, val edgeSoftness: Float,
    val flash: FlashSpec?,          // null = this camera has no flash look (Aqua, Plastic 120)
    val flareStyle: FlareStyleSpec,
    val frame: FrameFormat,         // default frame; FULL unless the type is square/instant/half
    val dateStampColor: String,     // "#RRGGBB"
    val randomColorShift: Float = 0f,      // 0..0.1, Cross Process / Expired 90s
    val leakBias: Float = 0f,              // 0..1 extra leak probability/strength (Plastic 120, Expired 90s)
    val lens: Map<LensKind, LensScale> = emptyMap(),
    val card: CardSpec,
)
// GrainSpec(amount, size), VignetteSpec(strength, softness), HalationSpec(strength, tint "#RRGGBB"),
// FlashSpec(hotspotSize, falloff, kelvin, strength, gels: List<String> = empty),
// LensScale(vignette = 1f, edgeSoftness = 1f, chromaticAberration = 1f),
// CardSpec(body: CardBody, bodyColor: "#RRGGBB", accentColor: "#RRGGBB")
enum class CardBody { DISPOSABLE, COMPACT, INSTANT, TOY }

data class EffectToggles(val grain: GrainLevel, val flare: FlareMode, val leaks: LeakMode)

class ProfileRepository(context: android.content.Context) {
    fun all(includeUnreleased: Boolean = false): List<CameraProfile>  // v1.0 only by default, PRD order
    fun byId(id: String): CameraProfile?
    suspend fun lut(id: String): Lut                                  // cached
}

// The ONLY place profile + user toggles + per-shot randomness become shader params.
object ParamsMapper {
    fun toFilmParams(
        profile: CameraProfile, toggles: EffectToggles, lens: LensKind,
        flashActive: Boolean, seed: Int, brightHighlight: Boolean,
    ): FilmParams
}
// Card art = three stacked vector drawables of one body shape (same 120x96 viewport):
// body (white, app tints with card.bodyColor), accent (white, tinted with card.accentColor),
// detail (fixed colours: lens, flash window, shading; drawn on top, never tinted).
data class CardLayers(@DrawableRes val body: Int, @DrawableRes val accent: Int, @DrawableRes val detail: Int)
fun CardBody.layers(): CardLayers
```

`tools/lut/generate_luts.py` (numpy, optional colour-science) writes `.cube` and the
strip PNG for every profile. With no reference photo pairs yet, it builds each LUT
from a parametric description (curves, channel mixing, split toning) per camera
type; it must be reproducible and documented so real reference pairs can replace it.

## `:camera` — `com.retrocam.camera`

Owns CameraX: binding, capture, flash, zoom/lens stops, focus, orientation, saving.

```kotlin
enum class Facing { BACK, FRONT }
enum class FlashMode { OFF, AUTO, ON }
data class ZoomStop(val ratio: Float, val label: String)            // e.g. 0.6f "0.6×"
data class CameraUiState(
    val facing: Facing, val hasFront: Boolean, val hasBack: Boolean, val hasFlashUnit: Boolean,
    val zoomRatio: Float, val minZoom: Float, val maxZoom: Float, val zoomStops: List<ZoomStop>,
    val lensKind: com.retrocam.camera.ActiveLens,                  // ULTRA_WIDE / MAIN / TELE / FRONT
)
enum class ActiveLens { ULTRA_WIDE, MAIN, TELE, FRONT }

class RetroCameraController(context: android.content.Context) {
    val state: kotlinx.coroutines.flow.StateFlow<CameraUiState?>
    val surfaceRequest: kotlinx.coroutines.flow.StateFlow<androidx.camera.core.SurfaceRequest?> // for CameraXViewfinder
    suspend fun bind(owner: androidx.lifecycle.LifecycleOwner, effect: androidx.camera.core.CameraEffect?)
    fun setFacing(facing: Facing)
    fun setFlashMode(mode: FlashMode)
    fun setZoomRatio(ratio: Float)                     // clamped to the allowed range
    fun setFullZoomUnlocked(unlocked: Boolean)         // default limit: longest optical lens, or 2x
    fun focusAt(x: Float, y: Float, width: Float, height: Float)
    fun setTargetRotation(surfaceRotation: Int)
    suspend fun capture(): CapturedFrame
    fun unbind()
}
// Upright bitmap (sensor rotation already applied, front camera NOT mirrored).
data class CapturedFrame(val bitmap: android.graphics.Bitmap, val flashFired: Boolean,
                         val lens: ActiveLens, val timestampMillis: Long)

class OrientationWatcher(context: android.content.Context) {   // OrientationEventListener with hysteresis
    val surfaceRotation: StateFlow<Int>  // Surface.ROTATION_* for ImageCapture
    val uiRotationDegrees: StateFlow<Float> // 0, 90, -90, 180: how much to rotate icons
    fun start(); fun stop()
}

class PhotoSaver(context: android.content.Context) {
    // JPEG q92 to Pictures/RetroCam via MediaStore, EXIF DateTimeOriginal + Orientation NORMAL,
    // GPS only when location != null. Returns the content Uri.
    suspend fun save(bitmap: android.graphics.Bitmap, takenAtMillis: Long, location: android.location.Location?): android.net.Uri
    suspend fun lastSaved(): android.net.Uri?   // RetroCam's own last photo (persisted), null if deleted
}
object ZoomStops { fun compute(minZoom: Float, maxZoom: Float, opticalRatios: List<Float>, unlocked: Boolean): List<ZoomStop> } // pure
```

## `:app` — `com.retrocam`

Single activity, Jetpack Compose + Material 3 (dynamic colour off, dark), one
screen per PRD "UX and interface". Owns: permission flow, `CameraViewModel`,
`SettingsRepository` (DataStore Preferences), capture orchestration
(`capture → ParamsMapper → OffscreenFilmRenderer → FrameComposer → DateStampRenderer → PhotoSaver`
on a background dispatcher so the next shot is not blocked), timer, shutter
sound (MediaActionSound) + haptic with mute, volume-key shutter, icon rotation
animation (200 ms), camera strip, settings sheet, adaptive app icon.

Manifest: `CAMERA`, `VIBRATE`, `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`
(requested only when the user enables EXIF location), and
`WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` (MediaStore on API 28
still needs it to write to Pictures/RetroCam). Activity:
`screenOrientation="portrait"` (layout fixed like the stock camera, never restarts on
rotation) plus `configChanges` for screen size/layout; `OrientationWatcher` drives the
capture rotation and the 200 ms icon/label rotation (see PRD orientation behaviour).
