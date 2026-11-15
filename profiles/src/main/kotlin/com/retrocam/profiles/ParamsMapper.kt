package com.retrocam.profiles

import com.retrocam.filmengine.FilmParams
import com.retrocam.filmengine.FlareLook
import com.retrocam.filmengine.FlareStyle
import com.retrocam.filmengine.FlashLook
import com.retrocam.filmengine.LeakLook
import com.retrocam.filmengine.RelightLook
import com.retrocam.filmengine.Rgb
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs

/**
 * The ONLY place profile + user toggles + per-shot randomness become shader params.
 *
 * Pure and deterministic: the same inputs (including [seed]) always give the same [FilmParams].
 * All randomness comes from `java.util.Random` instances seeded from [seed] (one stream per
 * feature, so toggling one effect never changes another's random values).
 */
object ParamsMapper {
    private const val SALT_FLASH = 0x1001L
    private const val SALT_FLARE = 0x2002L
    private const val SALT_LEAK = 0x3003L
    private const val SALT_SHIFT = 0x4004L

    /** Leak strength before [CameraProfile.leakBias] is added. */
    private const val LEAK_SUBTLE = 0.25f
    private const val LEAK_STRONG = 0.55f
    private const val LEAK_BIAS_GAIN = 0.3f
    private const val FLARE_STRENGTH = 0.6f

    fun toFilmParams(
        profile: CameraProfile,
        toggles: EffectToggles,
        lens: LensKind,
        flashActive: Boolean,
        seed: Int,
        brightHighlight: Boolean,
    ): FilmParams = toFilmParams(profile, toggles, lens, flashActive, seed, brightHighlight, null, null)

    /**
     * Same mapping, with the flare centred on the live preview highlight ([highlightX], [highlightY]
     * in 0..1 UV of the upright frame) when both are given; otherwise the centre is derived from [seed].
     */
    fun toFilmParams(
        profile: CameraProfile,
        toggles: EffectToggles,
        lens: LensKind,
        flashActive: Boolean,
        seed: Int,
        brightHighlight: Boolean,
        highlightX: Float? = null,
        highlightY: Float? = null,
        filmFlash: Boolean = false,
    ): FilmParams {
        val scale = profile.lens[lens] ?: LensScale()
        return FilmParams(
            exposureEv = profile.exposureEv.coerceIn(-1f, 1f),
            blackLift = profile.blackLift.coerceIn(0f, 0.15f),
            contrast = profile.contrast.coerceIn(0.7f, 1.4f),
            saturation = profile.saturation.coerceIn(0f, 2f),
            grainAmount = (profile.grain.amount * grainMultiplier(toggles.grain)).coerceIn(0f, 1f),
            grainSizePx = profile.grain.size.coerceIn(1f, 3f),
            vignetteStrength = (profile.vignette.strength * scale.vignette).coerceIn(0f, 1f),
            vignetteSoftness = profile.vignette.softness.coerceIn(0f, 1f),
            halationStrength = profile.halation.strength.coerceIn(0f, 1f),
            halationTint = parseHexRgb(profile.halation.tint),
            chromaticAberrationPx = (profile.chromaticAberration * scale.chromaticAberration).coerceIn(0f, 4f),
            edgeSoftness = (profile.edgeSoftness * scale.edgeSoftness).coerceIn(0f, 1f),
            // Film flash (capture only) also gets the flash colour tint, as if the flash had fired.
            flash = flashLook(profile, flashActive || filmFlash, seed),
            relight = if (filmFlash) relightLook(profile, stackedWithFlash = flashActive) else null,
            flare = flareLook(profile, toggles.flare, brightHighlight, seed, highlightX, highlightY),
            leak = leakLook(profile, toggles.leaks, seed),
            colorShift = colorShift(profile, seed),
            seed = seed,
        )
    }

    /** OFF 0, LOW 0.5, NORMAL 1, HIGH 1.6. */
    fun grainMultiplier(level: GrainLevel): Float = when (level) {
        GrainLevel.OFF -> 0f
        GrainLevel.LOW -> 0.5f
        GrainLevel.NORMAL -> 1f
        GrainLevel.HIGH -> 1.6f
    }

    private fun flashLook(profile: CameraProfile, flashActive: Boolean, seed: Int): FlashLook? {
        val spec = profile.flash
        if (!flashActive || spec == null) return null
        val gel = if (spec.gels.isEmpty()) null else parseHexRgb(spec.gels[random(seed, SALT_FLASH).nextInt(spec.gels.size)])
        return FlashLook(
            hotspotSize = spec.hotspotSize.coerceIn(0f, 1f),
            falloff = spec.falloff.coerceIn(0f, 1f),
            kelvin = spec.kelvin,
            strength = spec.strength.coerceIn(0f, 1f),
            gel = gel,
            backgroundDrop = spec.backgroundDrop.coerceIn(0f, 1f),
        )
    }

    // Film flash flavour: disposables / instant cameras (strong falloff, strong background drop) look punchy, others gentle.
    private const val RELIGHT_BASE = 0.176f
    private const val RELIGHT_GAIN = 0.32f      // strength = BASE + GAIN * spec.strength
    private const val RELIGHT_EXPOSURE_COMP = 3.5f // strength *= 2^(-COMP * exposureEv) for brightened profiles
    private const val RELIGHT_REACH_GENTLE = 1.3f
    private const val RELIGHT_REACH_PUNCHY = 0.9f
    private const val RELIGHT_DROP_GAIN = 1.0f  // ambient drop = spec.backgroundDrop * GAIN

    // Stacking with a real (or screen) flash that already fired: the frame is already flash-lit, so the relight is scaled
    // down (x0.55) and its ambient drop is skipped (the real flash look's own backgroundDrop already darkens the
    // background; applying both would crush it twice). The flash colour tint is applied once by flashLook().
    const val STACKED_STRENGTH_FACTOR = 0.55f

    /**
     * Film-flash look from the profile's flash spec; profiles without one (Aqua, Plastic 120) get a gentle default.
     * [stackedWithFlash] = a real flash already fired in this frame.
     */
    fun relightLook(profile: CameraProfile, stackedWithFlash: Boolean = false): RelightLook {
        val look = baseRelightLook(profile)
        return if (stackedWithFlash) look.copy(strength = look.strength * STACKED_STRENGTH_FACTOR, ambientDrop = 0f) else look
    }

    private fun baseRelightLook(profile: CameraProfile): RelightLook {
        // Profiles that already brighten the exposure (Instax +0.2 EV and a bright LUT) get less flash so faces do not clip.
        val headroom = Math.pow(2.0, -RELIGHT_EXPOSURE_COMP * profile.exposureEv.coerceIn(-1f, 1f).toDouble()).toFloat().coerceAtMost(1f)
        val spec = profile.flash
        if (spec == null) return RelightLook(kelvin = 5800f, strength = 0.7f * headroom, ambientDrop = 0.25f, reach = RELIGHT_REACH_GENTLE)
        val punch = ((spec.falloff.coerceIn(0f, 1f) - 0.3f) / 0.65f).coerceIn(0f, 1f)
        return RelightLook(
            kelvin = spec.kelvin,
            gel = null, // gels are a creative colour for the real-flash look; the film flash stays a natural white
            strength = (RELIGHT_BASE + RELIGHT_GAIN * spec.strength.coerceIn(0f, 1f)) * headroom,
            ambientDrop = (spec.backgroundDrop.coerceIn(0f, 1f) * RELIGHT_DROP_GAIN).coerceIn(0f, 1f),
            reach = RELIGHT_REACH_GENTLE + (RELIGHT_REACH_PUNCHY - RELIGHT_REACH_GENTLE) * punch,
        )
    }

    private fun flareLook(
        profile: CameraProfile,
        mode: FlareMode,
        brightHighlight: Boolean,
        seed: Int,
        highlightX: Float?,
        highlightY: Float?,
    ): FlareLook? {
        val present = when (mode) {
            FlareMode.OFF -> false
            FlareMode.ALWAYS -> true
            FlareMode.AUTO -> brightHighlight
        }
        if (!present) return null
        val rnd = random(seed, SALT_FLARE)
        // Draw all values unconditionally so the stream does not depend on the style.
        val styleCoin = rnd.nextBoolean()
        val defaultX = 0.2f + 0.6f * rnd.nextFloat()
        val defaultY = 0.15f + 0.35f * rnd.nextFloat()
        val style = when (profile.flareStyle) {
            FlareStyleSpec.NONE -> return null
            FlareStyleSpec.WARM -> FlareStyle.WARM
            FlareStyleSpec.RAINBOW -> FlareStyle.RAINBOW
            FlareStyleSpec.RANDOM -> if (styleCoin) FlareStyle.WARM else FlareStyle.RAINBOW
        }
        val useHighlight = highlightX != null && highlightY != null
        return FlareLook(
            style = style,
            strength = FLARE_STRENGTH,
            centerX = if (useHighlight) highlightX!!.coerceIn(0f, 1f) else defaultX,
            centerY = if (useHighlight) highlightY!!.coerceIn(0f, 1f) else defaultY,
        )
    }

    private fun leakLook(profile: CameraProfile, mode: LeakMode, seed: Int): LeakLook? {
        val base = when (mode) {
            LeakMode.OFF -> return null
            LeakMode.SUBTLE -> LEAK_SUBTLE
            LeakMode.STRONG -> LEAK_STRONG
        }
        val rnd = random(seed, SALT_LEAK)
        // Leaks are mostly warm (orange/red/magenta/yellow), occasionally any hue.
        val warm = rnd.nextFloat() < 0.75f
        val hueRaw = rnd.nextFloat()
        val hue = if (warm) (hueRaw * 80f - 25f + 360f) % 360f else hueRaw * 360f
        val saturation = 0.75f + 0.25f * rnd.nextFloat()
        val angle = rnd.nextFloat() * (2f * PI.toFloat())   // radians, 0..2*PI
        val offset = rnd.nextFloat()                          // 0..1 position along the leak direction
        return LeakLook(
            tint = hsvToRgb(hue, saturation, 1f),
            strength = (base + profile.leakBias.coerceIn(0f, 1f) * LEAK_BIAS_GAIN).coerceIn(0f, 1f),
            angle = angle,
            offset = offset,
        )
    }

    private fun colorShift(profile: CameraProfile, seed: Int): Rgb {
        val range = profile.randomColorShift.coerceIn(0f, 0.1f)
        if (range <= 0f) return Rgb(0f, 0f, 0f)
        val rnd = random(seed, SALT_SHIFT)
        fun next() = (rnd.nextFloat() * 2f - 1f) * range
        return Rgb(next(), next(), next())
    }

    /** One independent, well-mixed `java.util.Random` stream per (seed, feature). */
    private fun random(seed: Int, salt: Long): Random {
        // splitmix64 finaliser: adjacent seeds give unrelated streams.
        var z = seed.toLong() * -0x61c8864680b583ebL + salt
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return Random(z xor (z ushr 31))
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): Rgb {
        val c = v * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r, g, b) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return Rgb(r + m, g + m, b + m)
    }
}
