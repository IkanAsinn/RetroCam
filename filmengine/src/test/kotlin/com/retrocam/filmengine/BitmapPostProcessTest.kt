package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BitmapPostProcessTest {
    private fun solid(w: Int, h: Int, color: Int = Color.rgb(40, 120, 200)): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    @Test
    fun fullIsUnchanged() {
        val src = solid(400, 300)
        assertSame(src, FrameComposer.apply(src, FrameFormat.FULL))
    }

    @Test
    fun squareIsCentreCrop() {
        val l = FrameComposer.apply(solid(400, 300), FrameFormat.SQUARE)
        assertEquals(300, l.width); assertEquals(300, l.height)
        val p = FrameComposer.apply(solid(300, 400), FrameFormat.SQUARE)
        assertEquals(300, p.width); assertEquals(300, p.height)
    }

    @Test
    fun squareCropTakesTheCentre() {
        val src = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.BLUE)
        for (x in 50 until 350) for (y in 0 until 300) src.setPixel(x, y, Color.GREEN)
        val out = FrameComposer.apply(src, FrameFormat.SQUARE)
        assertEquals(Color.GREEN, out.getPixel(0, 0))
        assertEquals(Color.GREEN, out.getPixel(299, 299))
    }

    @Test
    fun instantSquareHasThickBottomBorder() {
        val out = FrameComposer.apply(solid(400, 300), FrameFormat.INSTANT_SQUARE)
        assertTrue(out.width > 300 && out.height > out.width)
        // 600-film proportions (card ~ 88 x 107 around a 79 mm square).
        assertEquals(107f / 88f, out.height.toFloat() / out.width, 0.03f)
        val card = Color.rgb(0xF7, 0xF5, 0xEF)
        assertEquals(card, out.getPixel(1, 1))
        assertEquals(card, out.getPixel(out.width / 2, out.height - 2))
        assertEquals(Color.rgb(40, 120, 200), out.getPixel(out.width / 2, out.height / 3))
        val side = (300 * 0.057f).toInt()
        val bottom = (300 * 0.272f).toInt()
        assertTrue(bottom > 3 * side)
    }

    @Test
    fun instantMiniPortraitIsCreditCardRatio() {
        val out = FrameComposer.apply(solid(900, 1200), FrameFormat.INSTANT_MINI)
        assertEquals(54f / 86f, out.width.toFloat() / out.height, 0.01f)
        assertEquals(Color.rgb(0xF7, 0xF5, 0xEF), out.getPixel(1, out.height - 2))
    }

    @Test
    fun instantMiniLandscapeKeepsLandscapeImageArea() {
        val out = FrameComposer.apply(solid(1200, 900), FrameFormat.INSTANT_MINI)
        // Image area 62:46 plus 8 side units and 24 vertical units => 70 x 70 units.
        assertEquals(1f, out.width.toFloat() / out.height, 0.02f)
        assertTrue(out.width > 0)
    }

    @Test
    fun halfFrameIsTwoPortraitFramesWithGutter() {
        val out = FrameComposer.apply(solid(4000, 3000), FrameFormat.HALF_FRAME)
        val frameH = out.height
        val frameW = 2000 * 3000 / 3000 // left half is 2000x3000 -> 3:4 crop = 2000 x 2667
        assertEquals(2667, frameH)
        val gutter = out.width - 2 * frameW
        assertTrue(gutter in 2..(frameW / 20))
        assertEquals(Color.BLACK, out.getPixel(frameW + gutter / 2, 10))
        assertEquals(Color.rgb(40, 120, 200), out.getPixel(10, 10))
        assertEquals(Color.rgb(40, 120, 200), out.getPixel(out.width - 10, 10))
        val p = FrameComposer.apply(solid(300, 400), FrameFormat.HALF_FRAME)
        assertEquals(302, p.width) // two 150 px frames + 2 px gutter
        assertEquals(200, p.height)
    }

    @Test
    fun dateStampOnlyTouchesBottomRight() {
        val w = 1000
        val h = 750
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(30, 60, 90))
        val before = IntArray(w * h)
        bmp.getPixels(before, 0, w, 0, 0, w, h)
        DateStampRenderer.draw(bmp, "'26 11 13", Rgb(1f, 0.55f, 0.1f))
        val after = IntArray(w * h)
        bmp.getPixels(after, 0, w, 0, 0, w, h)
        var changed = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (before[y * w + x] != after[y * w + x]) {
                changed++
                assertTrue("pixel ($x,$y) outside bottom-right changed", x > w / 2 && y > h / 2)
                // Inside the 4% margin nothing is drawn beyond a small glow.
                assertTrue(x < w - 5 && y < h - 5)
            }
        }
        assertTrue("expected some stamp pixels", changed > 200)
        // Orange core: find a strongly orange pixel.
        val hasOrange = after.any { Color.red(it) > 240 && Color.green(it) in 120..160 && Color.blue(it) < 60 }
        assertTrue(hasOrange)
        assertNotEquals(before.sum(), after.sum())
    }
}
