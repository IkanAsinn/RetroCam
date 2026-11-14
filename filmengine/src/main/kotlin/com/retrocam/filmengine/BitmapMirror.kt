package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Horizontal flip used to save front-camera photos the way the viewfinder shows them. */
object BitmapMirror {
    /**
     * Returns a new, mutable, horizontally mirrored copy of [src] and recycles [src] immediately, so at most two
     * full-size bitmaps are alive at once and the result can take the date stamp without another copy.
     */
    fun horizontal(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.scale(-1f, 1f, src.width / 2f, 0f)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }
}
