package com.retrocam.profiles

import com.retrocam.filmengine.FrameFormat
import kotlinx.serialization.Serializable

@Serializable
data class CameraProfile(
    val id: String,                 // snake_case, equals file names, e.g. "gold_snap"
    val name: String,               // in-app name, e.g. "Gold Snap" (no trademarks)
    val family: CameraFamily,
    val release: String,            // "1.0" or "1.1"
    val exposureEv: Float,
    val blackLift: Float,
    val contrast: Float,
    val saturation: Float = 1f,
    val grain: GrainSpec,
    val vignette: VignetteSpec,
    val halation: HalationSpec,
    val chromaticAberration: Float,
    val edgeSoftness: Float,
    val flash: FlashSpec?,          // null = this camera has no flash look (Aqua, Plastic 120)
    val flareStyle: FlareStyleSpec,
    val frame: FrameFormat,         // default frame; FULL unless the type is square/instant/half
    val dateStampColor: String,     // "#RRGGBB"
    val randomColorShift: Float = 0f,      // 0..0.1, Cross Process / Expired 90s
    val leakBias: Float = 0f,              // 0..1 extra leak probability/strength (Plastic 120, Expired 90s)
    val lens: Map<LensKind, LensScale> = emptyMap(),
    val card: CardSpec,
)

@Serializable data class GrainSpec(val amount: Float, val size: Float)

@Serializable data class VignetteSpec(val strength: Float, val softness: Float)

/** [tint] is "#RRGGBB". */
@Serializable data class HalationSpec(val strength: Float, val tint: String)

@Serializable
data class FlashSpec(
    val hotspotSize: Float,
    val falloff: Float,
    val kelvin: Float,
    val strength: Float,
    val gels: List<String> = emptyList(),
)

@Serializable
data class LensScale(
    val vignette: Float = 1f,
    val edgeSoftness: Float = 1f,
    val chromaticAberration: Float = 1f,
)

/** [bodyColor] and [accentColor] are "#RRGGBB". */
@Serializable data class CardSpec(val body: CardBody, val bodyColor: String, val accentColor: String)
