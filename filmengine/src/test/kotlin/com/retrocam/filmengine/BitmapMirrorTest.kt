package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BitmapMirrorTest {
    @Test
    fun flipsAThreeByTwoBitmapHorizontally() {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
        val src = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        for (y in 0 until 2) for (x in 0 until 3) src.setPixel(x, y, colors[y * 3 + x])

        val out = BitmapMirror.horizontal(src)

        assertEquals(3, out.width)
        assertEquals(2, out.height)
        for (y in 0 until 2) for (x in 0 until 3) assertEquals("pixel $x,$y", colors[y * 3 + (2 - x)], out.getPixel(x, y))
        assertTrue("source is released immediately", src.isRecycled)
        assertTrue("result can take the date stamp without another copy", out.isMutable)
    }
}
