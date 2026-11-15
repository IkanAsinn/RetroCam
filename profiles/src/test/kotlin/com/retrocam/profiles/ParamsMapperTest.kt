package com.retrocam.profiles

import com.retrocam.filmengine.FlareStyle
import com.retrocam.filmengine.FrameFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamsMapperTest {
    private val profiles = TestAssets.profiles().associateBy { it.id }
    private fun p(id: String) = profiles.getValue(id)

    private val normal = EffectToggles(GrainLevel.NORMAL, FlareMode.OFF, LeakMode.OFF)

    private fun map(
        profile: CameraProfile,
        toggles: EffectToggles = normal,
        lens: LensKind = LensKind.MAIN,
        flash: Boolean = false,
        seed: Int = 42,
        bright: Boolean = false,
    ) = ParamsMapper.toFilmParams(profile, toggles, lens, flash, seed, bright)

    @Test fun passesProfileValuesThrough() {
        val gold = p("gold_snap")
        val f = map(gold)
        assertEquals(gold.exposureEv, f.exposureEv, 0f)
        assertEquals(gold.blackLift, f.blackLift, 0f)
        assertEquals(gold.contrast, f.contrast, 0f)
        assertEquals(gold.grain.size, f.grainSizePx, 0f)
        assertEquals(gold.vignette.strength, f.vignetteStrength, 1e-6f)
        assertEquals(gold.vignette.softness, f.vignetteSoftness, 0f)
        assertEquals(gold.halation.strength, f.halationStrength, 0f)
        assertEquals(parseHexRgb(gold.halation.tint), f.halationTint)
        assertEquals(42, f.seed)
    }

    @Test fun grainLevelsScaleAmount() {
        val gold = p("gold_snap")
        fun grain(level: GrainLevel) = map(gold, EffectToggles(level, FlareMode.OFF, LeakMode.OFF)).grainAmount
        assertEquals(0f, grain(GrainLevel.OFF), 0f)
        assertEquals(gold.grain.amount * 0.5f, grain(GrainLevel.LOW), 1e-6f)
        assertEquals(gold.grain.amount, grain(GrainLevel.NORMAL), 1e-6f)
        assertEquals(gold.grain.amount * 1.6f, grain(GrainLevel.HIGH), 1e-6f)
    }

    @Test fun grainIsClampedToOne() {
        val heavy = p("power_800")   // 0.75 * 1.6 = 1.2 -> 1
        assertEquals(1f, map(heavy, EffectToggles(GrainLevel.HIGH, FlareMode.OFF, LeakMode.OFF)).grainAmount, 0f)
    }

    @Test fun lensScalingAppliesToVignetteEdgeAndAberration() {
        val gold = p("gold_snap")
        val main = map(gold, lens = LensKind.MAIN)
        val uw = map(gold, lens = LensKind.ULTRA_WIDE)
        val tele = map(gold, lens = LensKind.TELE)
        val front = map(gold, lens = LensKind.FRONT)
        assertTrue(uw.vignetteStrength > main.vignetteStrength)
        assertTrue(uw.edgeSoftness > main.edgeSoftness)
        assertTrue(uw.chromaticAberrationPx > main.chromaticAberrationPx)
        assertTrue(tele.vignetteStrength < main.vignetteStrength)
        assertTrue(tele.edgeSoftness < main.edgeSoftness)
        assertTrue(tele.chromaticAberrationPx < main.chromaticAberrationPx)
        assertEquals(main.vignetteStrength, front.vignetteStrength, 1e-6f)
        assertEquals(main.chromaticAberrationPx, front.chromaticAberrationPx, 1e-6f)
        val s = gold.lens.getValue(LensKind.ULTRA_WIDE)
        assertEquals(gold.chromaticAberration * s.chromaticAberration, uw.chromaticAberrationPx, 1e-5f)
        // Clamped to the shader ranges.
        val plastic = map(p("plastic_120"), lens = LensKind.ULTRA_WIDE)
        assertTrue(plastic.vignetteStrength <= 1f && plastic.edgeSoftness <= 1f && plastic.chromaticAberrationPx <= 4f)
    }

    @Test fun missingLensEntryMeansUnscaled() {
        val bare = p("gold_snap").copy(lens = emptyMap())
        assertEquals(bare.vignette.strength, map(bare, lens = LensKind.ULTRA_WIDE).vignetteStrength, 1e-6f)
    }

    @Test fun flashBackgroundDropIsPassedThrough() {
        val spec = p("gold_snap").flash!!
        assertEquals(spec.backgroundDrop.coerceIn(0f, 1f), map(p("gold_snap"), flash = true).flash!!.backgroundDrop, 0f)
    }

    @Test fun flashOnlyWhenActiveAndSpecified() {
        assertNull(map(p("gold_snap"), flash = false).flash)
        val look = map(p("gold_snap"), flash = true).flash
        assertNotNull(look)
        val spec = p("gold_snap").flash!!
        assertEquals(spec.hotspotSize, look!!.hotspotSize, 0f)
        assertEquals(spec.falloff, look.falloff, 0f)
        assertEquals(spec.kelvin, look.kelvin, 0f)
        assertEquals(spec.strength, look.strength, 0f)
        assertNull(look.gel)
        assertNull(map(p("aqua"), flash = true).flash)
        assertNull(map(p("plastic_120"), flash = true).flash)
    }

    @Test fun filmFlashIsOffByDefaultAndAppliesFlashLookWhenOn() {
        assertNull(map(p("gold_snap")).relight)
        fun film(id: String) = ParamsMapper.toFilmParams(p(id), normal, LensKind.MAIN, false, 42, false, null, null, true)
        val g = film("gold_snap")
        assertNotNull(g.relight)
        assertNotNull("film flash applies the flash tint / drop like a fired flash", g.flash)
        assertNotNull(film("aqua").relight) // no flash spec: gentle default
        assertNull(film("aqua").flash)
        assertNull(ParamsMapper.toFilmParams(p("gold_snap"), normal, LensKind.MAIN, true, 42, false).relight) // a real flash is not a film flash
    }

    @Test fun filmFlashStacksWithARealFlashGentlyAndTintsOnce() {
        fun map(id: String, flashFired: Boolean, film: Boolean) =
            ParamsMapper.toFilmParams(p(id), normal, LensKind.MAIN, flashFired, 42, false, null, null, film)
        for (id in listOf("gold_snap", "studio_400", "aqua")) {
            val alone = map(id, false, true)
            val stacked = map(id, true, true)
            assertNotNull(stacked.relight)
            assertEquals(alone.relight!!.strength * ParamsMapper.STACKED_STRENGTH_FACTOR, stacked.relight!!.strength, 1e-6f)
            assertEquals("duplicate background drop is skipped", 0f, stacked.relight!!.ambientDrop, 0f)
            assertEquals(alone.relight!!.reach, stacked.relight!!.reach, 0f)
            assertEquals("flash tint applied once, identical to a real flash", map(id, true, false).flash, stacked.flash)
            assertNull("real flash alone adds no relight", map(id, true, false).relight)
        }
        assertTrue(map("gold_snap", false, true).relight!!.ambientDrop > 0f)
    }

    @Test fun filmFlashFlavourDisposablesPunchierThanGentleProfiles() {
        val disposable = ParamsMapper.relightLook(p("gold_snap"))
        val gentle = ParamsMapper.relightLook(p("studio_400"))
        assertTrue(disposable.reach < gentle.reach)
        assertTrue(disposable.ambientDrop > gentle.ambientDrop)
        for (profile in profiles.values) {
            val l = ParamsMapper.relightLook(profile)
            assertTrue(profile.id, l.strength in 0.05f..1.2f && l.ambientDrop in 0f..1f && l.reach in 0.5f..1.5f)
        }
    }

    @Test fun gelsAreChosenDeterministicallyFromSeed() {
        val pop = p("pop_simple")
        val gels = pop.flash!!.gels.map { parseHexRgb(it) }.toSet()
        assertEquals(3, gels.size)
        val seen = mutableSetOf<Any?>()
        for (seed in 0 until 60) {
            val a = map(pop, flash = true, seed = seed).flash!!.gel
            val b = map(pop, flash = true, seed = seed).flash!!.gel
            assertEquals(a, b)
            assertTrue(a in gels)
            seen += a
        }
        assertEquals("all three gels should appear over 60 seeds", 3, seen.size)
    }

    @Test fun flareModes() {
        fun flare(profile: CameraProfile, mode: FlareMode, bright: Boolean) =
            map(profile, EffectToggles(GrainLevel.NORMAL, mode, LeakMode.OFF), bright = bright).flare
        val gold = p("gold_snap")   // WARM
        assertNull(flare(gold, FlareMode.OFF, true))
        assertNull(flare(gold, FlareMode.AUTO, false))
        assertNotNull(flare(gold, FlareMode.AUTO, true))
        assertNotNull(flare(gold, FlareMode.ALWAYS, false))
        assertEquals(FlareStyle.WARM, flare(gold, FlareMode.ALWAYS, false)!!.style)
        // NONE always yields null.
        val none = p("silver_100")
        assertEquals(FlareStyleSpec.NONE, none.flareStyle)
        assertNull(flare(none, FlareMode.ALWAYS, true))
        // RAINBOW
        assertEquals(FlareStyle.RAINBOW, flare(p("vivid_100"), FlareMode.ALWAYS, false)!!.style)
    }

    @Test fun randomFlareStyleComesFromSeedAndUsesBothStyles() {
        val toy = p("plastic_120")
        assertEquals(FlareStyleSpec.RANDOM, toy.flareStyle)
        val toggles = EffectToggles(GrainLevel.NORMAL, FlareMode.ALWAYS, LeakMode.OFF)
        val styles = (0 until 40).map { map(toy, toggles, seed = it).flare!!.style }
        assertEquals(setOf(FlareStyle.WARM, FlareStyle.RAINBOW), styles.toSet())
        assertEquals(styles, (0 until 40).map { map(toy, toggles, seed = it).flare!!.style })
    }

    @Test fun flareCentreDefaultsFromSeedAndFollowsHighlight() {
        val toggles = EffectToggles(GrainLevel.NORMAL, FlareMode.ALWAYS, LeakMode.OFF)
        val gold = p("gold_snap")
        val def = map(gold, toggles, seed = 7).flare!!
        assertTrue(def.centerX in 0f..1f && def.centerY in 0f..1f)
        assertNotEquals(def.centerX, map(gold, toggles, seed = 8).flare!!.centerX)
        val withHighlight = ParamsMapper.toFilmParams(
            gold, toggles, LensKind.MAIN, false, 7, true, highlightX = 0.9f, highlightY = 0.1f,
        ).flare!!
        assertEquals(0.9f, withHighlight.centerX, 0f)
        assertEquals(0.1f, withHighlight.centerY, 0f)
        // Only one coordinate given: fall back to the seeded default.
        val half = ParamsMapper.toFilmParams(gold, toggles, LensKind.MAIN, false, 7, true, highlightX = 0.9f).flare!!
        assertEquals(def.centerX, half.centerX, 0f)
    }

    @Test fun leaks() {
        fun leak(profile: CameraProfile, mode: LeakMode, seed: Int = 5) =
            map(profile, EffectToggles(GrainLevel.NORMAL, FlareMode.OFF, mode), seed = seed).leak
        val gold = p("gold_snap")
        assertNull(leak(gold, LeakMode.OFF))
        val subtle = leak(gold, LeakMode.SUBTLE)!!
        val strong = leak(gold, LeakMode.STRONG)!!
        assertEquals(0.25f, subtle.strength, 1e-6f)
        assertEquals(0.55f, strong.strength, 1e-6f)
        assertTrue(subtle.offset in 0f..1f)
        assertTrue(subtle.angle in 0f..(2 * Math.PI.toFloat() + 1e-4f))
        listOf(subtle.tint.r, subtle.tint.g, subtle.tint.b).forEach { assertTrue(it in 0f..1f) }
        // Same seed -> same random placement regardless of strength.
        assertEquals(subtle.angle, strong.angle, 0f)
        assertEquals(subtle.tint, strong.tint)
        // Different seeds -> different leaks.
        val angles = (0 until 20).map { leak(gold, LeakMode.SUBTLE, it)!!.angle }.toSet()
        assertTrue(angles.size > 15)
        // Leak bias raises strength on leaky cameras and stays <= 1.
        val plastic = leak(p("plastic_120"), LeakMode.STRONG)!!
        assertTrue(plastic.strength > strong.strength)
        assertTrue(plastic.strength <= 1f)
    }

    @Test fun colorShiftStaysWithinProfileRange() {
        val cross = p("cross_process")
        val range = cross.randomColorShift
        assertTrue(range > 0f)
        val seen = mutableSetOf<Float>()
        for (seed in -50..200) {
            val s = map(cross, seed = seed).colorShift
            assertTrue(s.r in -range..range && s.g in -range..range && s.b in -range..range)
            seen += s.r
        }
        assertTrue("shift should vary per seed", seen.size > 100)
        val none = map(p("gold_snap")).colorShift
        assertEquals(0f, none.r, 0f); assertEquals(0f, none.g, 0f); assertEquals(0f, none.b, 0f)
    }

    @Test fun deterministicForASeed() {
        val toggles = EffectToggles(GrainLevel.HIGH, FlareMode.ALWAYS, LeakMode.STRONG)
        for (profile in profiles.values) {
            for (seed in listOf(0, 1, -1, 123456789, Int.MIN_VALUE)) {
                val a = map(profile, toggles, LensKind.ULTRA_WIDE, true, seed, true)
                val b = map(profile, toggles, LensKind.ULTRA_WIDE, true, seed, true)
                assertEquals("${profile.id}/$seed", a, b)
            }
        }
        val a = map(p("expired_90s"), EffectToggles(GrainLevel.NORMAL, FlareMode.ALWAYS, LeakMode.SUBTLE), seed = 1)
        val b = map(p("expired_90s"), EffectToggles(GrainLevel.NORMAL, FlareMode.ALWAYS, LeakMode.SUBTLE), seed = 2)
        assertNotEquals(a, b)
    }

    @Test fun everyProfileMapsIntoShaderRanges() {
        val toggles = EffectToggles(GrainLevel.HIGH, FlareMode.ALWAYS, LeakMode.STRONG)
        for (profile in profiles.values) for (lens in LensKind.entries) {
            val f = map(profile, toggles, lens, true, 99, true)
            assertTrue(profile.id, f.grainAmount in 0f..1f)
            assertTrue(profile.id, f.vignetteStrength in 0f..1f)
            assertTrue(profile.id, f.edgeSoftness in 0f..1f)
            assertTrue(profile.id, f.chromaticAberrationPx in 0f..4f)
            assertTrue(profile.id, f.exposureEv in -1f..1f)
            assertTrue(profile.id, f.contrast in 0.7f..1.4f)
        }
    }

    @Test fun hexParser() {
        assertEquals(com.retrocam.filmengine.Rgb(1f, 138 / 255f, 31 / 255f), parseHexRgb("#FF8A1F"))
        assertEquals(0xFFFF8A1F.toInt(), parseHexArgb("#FF8A1F"))
        assertEquals(0f, parseHexRgb("000000").r, 0f)
        for (bad in listOf("#FFF", "#GG0000", "", "#1234567")) {
            val ok = runCatching { parseHexRgb(bad) }.isFailure
            assertTrue(bad, ok)
        }
    }

    @Test fun frameFormatPassesThroughProfile() {
        assertEquals(FrameFormat.HALF_FRAME, p("half_frame").frame)
    }
}
