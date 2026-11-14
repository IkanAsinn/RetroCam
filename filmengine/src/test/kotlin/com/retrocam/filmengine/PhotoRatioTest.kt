package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoRatioTest {
    private val sizes = listOf(
        3 to 4, 4 to 3, 3000 to 4000, 4000 to 3000, 3001 to 4001, 4001 to 3001, 4031 to 3023, 3023 to 4031,
        1 to 1, 7 to 9, 9 to 7, 101 to 103, 1080 to 1920, 1920 to 1080, 4608 to 3456, 3456 to 4608,
    )

    private fun Rect.l() = listOf(left, top, right, bottom)

    @Test
    fun rectIsInsideSourceCentredAndHasTheRightAspect() {
        for (ratio in PhotoRatio.entries) for ((w, h) in sizes) {
            val r = FrameComposer.ratioRect(w, h, ratio)
            val tag = "$ratio ${w}x$h -> $r"
            assertTrue(tag, r.left >= 0 && r.top >= 0 && r.right <= w && r.bottom <= h)
            assertTrue(tag, r.width() >= 1 && r.height() >= 1)
            assertTrue("centred $tag", abs(r.left - (w - r.right)) <= 1 && abs(r.top - (h - r.bottom)) <= 1)
            // Portrait source (h >= w): W:H of the ratio; landscape source: H:W.
            val portrait = h >= w
            val rw = if (portrait) ratio.portraitW else ratio.portraitH
            val rh = if (portrait) ratio.portraitH else ratio.portraitW
            // The rect is the largest one: it spans the full source width or height.
            assertTrue("largest $tag", r.width() == w || r.height() == h)
            // Aspect within 1 px on the derived side (tiny sources like 3x4 can hit the 1 px minimum).
            if (r.width() == w) {
                assertTrue("aspect $tag", abs(r.height() - w.toDouble() * rh / rw) <= 1.0 || r.height() == 1)
            } else {
                assertTrue("aspect $tag", abs(r.width() - h.toDouble() * rw / rh) <= 1.0 || r.width() == 1)
            }
        }
    }

    @Test
    fun exactRects() {
        assertEquals(listOf(0, 0, 3000, 4000), FrameComposer.ratioRect(3000, 4000, PhotoRatio.R4_3).l())
        assertEquals(listOf(166, 0, 2833, 4000), FrameComposer.ratioRect(3000, 4000, PhotoRatio.R3_2).l())
        assertEquals(listOf(375, 0, 2625, 4000), FrameComposer.ratioRect(3000, 4000, PhotoRatio.R16_9).l())
        assertEquals(listOf(0, 500, 3000, 3500), FrameComposer.ratioRect(3000, 4000, PhotoRatio.R1_1).l())
        // Landscape: the same ratios turned on their side.
        assertEquals(listOf(0, 0, 4000, 3000), FrameComposer.ratioRect(4000, 3000, PhotoRatio.R4_3).l())
        assertEquals(listOf(0, 166, 4000, 2833), FrameComposer.ratioRect(4000, 3000, PhotoRatio.R3_2).l())
        assertEquals(listOf(0, 375, 4000, 2625), FrameComposer.ratioRect(4000, 3000, PhotoRatio.R16_9).l())
        assertEquals(listOf(500, 0, 3500, 3000), FrameComposer.ratioRect(4000, 3000, PhotoRatio.R1_1).l())
    }

    /** The preview mask is one fixed portrait rect in the 3:4 sensor box; turning the phone must give the same crop turned. */
    @Test
    fun portraitAndLandscapeCropsAreTheSameRegionOfTheSensor() {
        for (ratio in PhotoRatio.entries) {
            val p = FrameComposer.ratioRect(3000, 4000, ratio)
            val l = FrameComposer.ratioRect(4000, 3000, ratio)
            assertEquals(p.width(), l.height())
            assertEquals(p.height(), l.width())
        }
    }

    @Test
    fun fullFrameReturnsTheSameBitmap() {
        val src = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        assertSame(src, FrameComposer.applyRatio(src, PhotoRatio.R4_3))
    }

    @Test
    fun applyRatioCropsPortraitAndLandscape() {
        val portrait = FrameComposer.applyRatio(Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888), PhotoRatio.R16_9)
        assertEquals(225, portrait.width)
        assertEquals(400, portrait.height)
        val landscape = FrameComposer.applyRatio(Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888), PhotoRatio.R3_2)
        assertEquals(400, landscape.width)
        assertEquals(267, landscape.height)
        val square = FrameComposer.applyRatio(Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888), PhotoRatio.R1_1)
        assertEquals(300, square.width)
        assertEquals(300, square.height)
    }
}
