package com.retrocam.filmengine

/** Every value the shader needs. Pure data, no Android types. */
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
    val relight: RelightLook? = null,      // film flash (capture only; the preview never sets it); null = off
    val flare: FlareLook? = null,          // null = no flare
    val leak: LeakLook? = null,            // null = no light leak
    val colorShift: Rgb = Rgb(0f, 0f, 0f), // per-shot random cast, added after LUT (-0.1..0.1)
    val seed: Int = 0,                     // per-shot random seed: grain pattern, leak position, flare
    val strength: Float = 1f,              // 0..1 filter strength: mix(unprocessed input, film result, strength); 1 = full look
) {
    companion object {
        const val REFERENCE_SHORT_EDGE = 1080
    }
}

data class Rgb(val r: Float, val g: Float, val b: Float)

data class FlashLook(
    val hotspotSize: Float,
    val falloff: Float,
    val kelvin: Float,
    val strength: Float,
    val gel: Rgb? = null,
    val backgroundDrop: Float = 0f, // 0..1: pushes dark, far-from-centre regions toward black (disposable-flash falloff)
)

/** Centre in 0..1 UV. */
data class FlareLook(val style: FlareStyle, val strength: Float, val centerX: Float, val centerY: Float)

enum class FlareStyle { WARM, RAINBOW }

data class LeakLook(val tint: Rgb, val strength: Float, val angle: Float, val offset: Float)
