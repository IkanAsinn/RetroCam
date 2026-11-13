package com.retrocam.profiles

import com.retrocam.filmengine.FrameFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAssetsTest {
    private val profiles = TestAssets.profiles()

    @Test fun everyJsonParses() {
        assertEquals(25, TestAssets.profileFiles.size)
        TestAssets.profileFiles.forEach { TestAssets.parse(it) }
    }

    @Test fun counts() {
        assertEquals(25, profiles.size)
        assertEquals(20, profiles.count { it.release == "1.0" })
        assertEquals(5, profiles.count { it.release == "1.1" })
        assertTrue(profiles.all { it.release == "1.0" || it.release == "1.1" })
    }

    @Test fun idsAreUniqueSnakeCaseAndMatchFiles() {
        assertEquals(profiles.size, profiles.map { it.id }.toSet().size)
        for (file in TestAssets.profileFiles) {
            val p = TestAssets.parse(file)
            assertEquals(file.nameWithoutExtension, p.id)
            assertTrue(p.id, Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*").matches(p.id))
            assertTrue("missing LUT for ${p.id}", File(TestAssets.lutsDir, "${p.id}.png").isFile)
        }
        val lutIds = TestAssets.lutsDir.listFiles { f -> f.extension == "png" }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(profiles.map { it.id }.toSet(), lutIds)
    }

    @Test fun namesAreDistinctAndTrademarkFree() {
        assertEquals(profiles.size, profiles.map { it.name }.toSet().size)
        val banned = listOf(
            "kodak", "fuji", "ilford", "polaroid", "instax", "lomo", "agfa", "cinestill", "portra", "ektar",
            "velvia", "tri-x", "holga", "diana", "contax", "olympus", "funsaver", "quicksnap", "superia",
        )
        for (p in profiles) {
            val text = (p.name + " " + p.id).lowercase()
            banned.forEach { assertTrue("${p.id} contains $it", it !in text) }
        }
    }

    @Test fun numericFieldsWithinPrdRanges() {
        for (p in profiles) {
            val tag = p.id
            assertIn(tag, "exposureEv", p.exposureEv, -1f, 1f)
            assertIn(tag, "blackLift", p.blackLift, 0f, 0.15f)
            assertIn(tag, "contrast", p.contrast, 0.7f, 1.4f)
            assertIn(tag, "saturation", p.saturation, 0f, 2f)
            assertIn(tag, "grain.amount", p.grain.amount, 0f, 1f)
            assertIn(tag, "grain.size", p.grain.size, 1f, 3f)
            assertIn(tag, "vignette.strength", p.vignette.strength, 0f, 1f)
            assertIn(tag, "vignette.softness", p.vignette.softness, 0f, 1f)
            assertIn(tag, "halation.strength", p.halation.strength, 0f, 1f)
            assertIn(tag, "chromaticAberration", p.chromaticAberration, 0f, 4f)
            assertIn(tag, "edgeSoftness", p.edgeSoftness, 0f, 1f)
            assertIn(tag, "randomColorShift", p.randomColorShift, 0f, 0.1f)
            assertIn(tag, "leakBias", p.leakBias, 0f, 1f)
            p.flash?.let {
                assertIn(tag, "flash.hotspotSize", it.hotspotSize, 0f, 1f)
                assertIn(tag, "flash.falloff", it.falloff, 0f, 1f)
                assertIn(tag, "flash.kelvin", it.kelvin, 2500f, 12000f)
                assertIn(tag, "flash.strength", it.strength, 0f, 1f)
            }
            for ((kind, s) in p.lens) {
                assertIn(tag, "lens[$kind].vignette", s.vignette, 0f, 3f)
                assertIn(tag, "lens[$kind].edgeSoftness", s.edgeSoftness, 0f, 3f)
                assertIn(tag, "lens[$kind].chromaticAberration", s.chromaticAberration, 0f, 3f)
            }
        }
    }

    @Test fun colourStringsParse() {
        for (p in profiles) {
            val all = listOf(p.dateStampColor, p.halation.tint, p.card.bodyColor, p.card.accentColor) +
                (p.flash?.gels ?: emptyList())
            all.forEach {
                assertTrue("${p.id}: $it", Regex("#[0-9A-Fa-f]{6}").matches(it))
                parseHexRgb(it)
            }
        }
    }

    @Test fun cardColoursAreDistinctPerType() {
        val pairs = profiles.map { it.card.bodyColor.uppercase() to it.card.accentColor.uppercase() }
        assertEquals("every camera card should look different", profiles.size, pairs.toSet().size)
        profiles.forEach { assertTrue(it.card.bodyColor != it.card.accentColor) }
    }

    @Test fun specificProfileFacts() {
        fun p(id: String) = profiles.first { it.id == id }
        assertEquals(0.7f, p("airy_400").exposureEv, 1e-6f)
        assertEquals(FrameFormat.INSTANT_SQUARE, p("instant_square").frame)
        assertTrue(p("instant_square").grain.amount < 0.1f)
        assertTrue(p("instant_square").edgeSoftness >= 0.6f)
        assertEquals(FrameFormat.INSTANT_MINI, p("instant_mini").frame)
        assertEquals(FrameFormat.SQUARE, p("plastic_120").frame)
        assertEquals(FrameFormat.HALF_FRAME, p("half_frame").frame)
        assertEquals(null, p("plastic_120").flash)
        assertEquals(null, p("aqua").flash)
        assertTrue(p("plastic_120").leakBias >= 0.5f)
        assertTrue(p("plastic_120").vignette.strength >= 0.8f)
        assertTrue(p("night_800t").halation.strength >= 0.7f)
        assertTrue(p("cross_process").randomColorShift > 0f)
        assertTrue(p("expired_90s").randomColorShift > 0f)
        assertEquals(listOf(3), listOf(p("pop_simple").flash!!.gels.size))
        assertEquals(CardBody.TOY, p("cross_process").card.body)
        assertEquals(CardBody.DISPOSABLE, p("mono_snap").card.body)
        // Every type has a per-lens table where ultra-wide boosts and tele reduces.
        for (profile in profiles) {
            val uw = profile.lens.getValue(LensKind.ULTRA_WIDE)
            val tele = profile.lens.getValue(LensKind.TELE)
            assertTrue(profile.id, uw.vignette in 1.25f..1.65f && uw.chromaticAberration in 1.25f..1.65f)
            assertTrue(profile.id, tele.vignette < 1f && tele.chromaticAberration < 1f)
        }
    }

    @Test fun orderMatchesPrd() {
        val repo = TestAssets.repository()
        assertEquals(TestAssets.prdOrder, repo.all(includeUnreleased = true).map { it.id })
        assertEquals(TestAssets.prdOrder.filter { id -> profiles.first { it.id == id }.release == "1.0" }, repo.all().map { it.id })
        assertEquals(20, repo.all().size)
        assertEquals(25, repo.all(true).size)
        assertNotNull(repo.byId("purple_haze"))
        assertEquals(null, repo.byId("nope"))
        // index.json lists exactly the profile files.
        assertEquals(TestAssets.profileFiles.map { it.nameWithoutExtension }.toSet(), TestAssets.prdOrder.toSet())
    }

    // ---- LUT PNGs -------------------------------------------------------------------------

    private fun readLut(id: String) = ImageIO.read(File(TestAssets.lutsDir, "$id.png"))

    private fun rgbAt(img: java.awt.image.BufferedImage, r: Int, g: Int, b: Int): IntArray {
        val px = img.getRGB(b * 33 + r, g)
        return intArrayOf((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
    }

    private fun luma(c: IntArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    @Test fun lutsAre1089By33() {
        for (p in profiles) {
            val img = readLut(p.id)
            assertEquals(p.id, 1089, img.width)
            assertEquals(p.id, 33, img.height)
        }
    }

    @Test fun lutsMapGreyPlausiblyAndDoNotClipEverywhere() {
        for (p in profiles) {
            val img = readLut(p.id)
            val mid = rgbAt(img, 16, 16, 16)
            mid.forEach { assertTrue("${p.id} mid-grey channel $it", it in 60..210) }
            assertTrue("${p.id} mid luma", luma(mid) in 100.0..175.0)
            assertTrue("${p.id} white brighter than black", luma(rgbAt(img, 32, 32, 32)) > luma(rgbAt(img, 0, 0, 0)) + 150)
            var clipped = 0
            for (b in 0..32) for (g in 0..32) for (r in 0..32) {
                if (rgbAt(img, r, g, b).any { it == 0 || it == 255 }) clipped++
            }
            assertTrue("${p.id} clips ${clipped}/35937 entries", clipped < 35937 * 0.9)
            // Tonal order is preserved along the grey axis (no solarisation / inversion).
            val ys = (0..32).map { luma(rgbAt(img, it, it, it)) }
            for (i in 1..32) assertTrue("${p.id} grey ramp step $i", ys[i] >= ys[i - 1] - 1.0)
        }
    }

    @Test fun layoutIsBlueMajorXRedMinorXGreenY() {
        // Mono looks have R=G=B everywhere, so a transposed layout would still pass; use a colour look:
        // in a strongly saturated look, red input must produce more red than blue input does.
        val img = readLut("vivid_100")
        val red = rgbAt(img, 32, 0, 0)
        val blue = rgbAt(img, 0, 0, 32)
        val green = rgbAt(img, 0, 32, 0)
        assertTrue(red[0] > red[1] && red[0] > red[2])
        assertTrue(blue[2] > blue[0] && blue[2] > blue[1])
        assertTrue(green[1] > green[0] && green[1] > green[2])
    }

    @Test fun monochromeLutsAreNeutral() {
        for (id in listOf("mono_snap", "grit_400", "silver_100")) {
            val img = readLut(id)
            for (b in 0..32 step 4) for (g in 0..32 step 4) for (r in 0..32 step 4) {
                val c = rgbAt(img, r, g, b)
                // A faint split-tone is allowed, anything bigger is a colour leak.
                assertTrue("$id ($r,$g,$b) = ${c.toList()}", c.max() - c.min() <= 40)
            }
            val mid = rgbAt(img, 16, 16, 16)
            assertTrue("$id mid-grey tint", mid.max() - mid.min() <= 8)
        }
    }

    @Test fun lutsAreDistinctFromEachOther() {
        val imgs = profiles.associate { it.id to readLut(it.id) }
        val ids = imgs.keys.toList()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            var sum = 0L
            val a = imgs.getValue(ids[i])
            val b = imgs.getValue(ids[j])
            for (y in 0 until 33) for (x in 0 until 1089) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                sum += abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)) +
                    abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)) + abs((p and 0xFF) - (q and 0xFF))
            }
            val meanPerChannel = sum / (33.0 * 1089 * 3)
            assertTrue("${ids[i]} vs ${ids[j]} too similar: $meanPerChannel", meanPerChannel > 3.0)
        }
    }

    @Test fun cardDrawablesExistForEveryBody() {
        val res = File("src/main/res/drawable")
        for (body in listOf("disposable", "compact", "instant", "toy")) for (layer in listOf("body", "accent", "detail")) {
            assertTrue("card_${body}_$layer.xml", File(res, "card_${body}_$layer.xml").isFile)
        }
        assertTrue(!File(res, "card_toy.xml").exists())
    }

    private fun assertIn(tag: String, field: String, v: Float, lo: Float, hi: Float) {
        assertTrue("$tag.$field=$v not in [$lo, $hi]", v in lo..hi)
    }
}
