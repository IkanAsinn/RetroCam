package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FrameFormat { FULL, SQUARE, INSTANT_SQUARE, INSTANT_MINI, HALF_FRAME }

/**
 * Aspect ratio of a FULL-format photo, written for the PORTRAIT orientation (width : height).
 * A landscape photo uses the transposed ratio (4:3, 3:2, 16:9). [R4_3] is the full sensor frame: no crop.
 */
enum class PhotoRatio(val portraitW: Int, val portraitH: Int) {
    R4_3(3, 4), R3_2(2, 3), R16_9(9, 16), R1_1(1, 1);

    /** Portrait width / height, e.g. 0.5625 for 16:9. This is also the preview mask aspect in the fixed 3:4 viewfinder box. */
    val portraitAspect: Float get() = portraitW.toFloat() / portraitH
    val isFullFrame: Boolean get() = this == R4_3
}

/**
 * Post-processing on the finished Bitmap (capture path only; preview draws these as Compose overlays).
 *
 * Output geometry (the source is never modified; except FULL the result is a new bitmap):
 *  - FULL: the source instance, unchanged.
 *  - SQUARE: centre square crop, side = min(w, h).
 *  - INSTANT_SQUARE: centre square crop of side `s` on an off-white (#F7F5EF) card, 600-film
 *    proportions: side margins 5.7 % of s, top 8.2 %, bottom 27.2 % (card 1.114 s x 1.354 s).
 *  - INSTANT_MINI: image area centre-cropped to 46:62 (portrait source) or 62:46 (landscape
 *    source) - the long axis follows the source orientation - on the same off-white card with
 *    margins of 4 / 5 / 19 units (sides / top / bottom) where 1 unit = image-short-side / 46.
 *    A portrait source therefore yields a 54:86 card. The thick edge is always at the bottom.
 *  - HALF_FRAME: two portrait 3:4 frames side by side taken from the LEFT and RIGHT halves of
 *    the photo (each centre-cropped), separated by a black gutter of ~1.2 % of the frame width
 *    (min 2 px).
 */
object FrameComposer {
    private val CARD_COLOR = Color.rgb(0xF7, 0xF5, 0xEF)

    fun apply(src: Bitmap, format: FrameFormat): Bitmap = when (format) {
        FrameFormat.FULL -> src
        FrameFormat.SQUARE -> {
            val side = min(src.width, src.height)
            crop(src, centered(src.width, src.height, 1f))
                .also { check(it.width == side && it.height == side) }
        }
        FrameFormat.INSTANT_SQUARE -> {
            val s = min(src.width, src.height)
            val side = (s * 0.057f).roundToInt()
            val top = (s * 0.082f).roundToInt()
            val bottom = (s * 0.272f).roundToInt()
            val photo = crop(src, centered(src.width, src.height, 1f))
            card(photo, side, top, side, bottom)
        }
        FrameFormat.INSTANT_MINI -> {
            val portrait = src.height >= src.width
            val aspect = if (portrait) 46f / 62f else 62f / 46f
            val photo = crop(src, centered(src.width, src.height, aspect))
            val unit = min(photo.width, photo.height) / 46f
            card(
                photo,
                (4 * unit).roundToInt(), (5 * unit).roundToInt(),
                (4 * unit).roundToInt(), (19 * unit).roundToInt(),
            )
        }
        FrameFormat.HALF_FRAME -> halfFrame(src)
    }

    /**
     * Applies [ratio] to the upright [src] (FULL format only). Portrait sources (height >= width) get W:H, landscape
     * sources H:W, so the ratio always describes the photo as it was taken. Returns [src] itself for 4:3 or when the
     * crop would change nothing.
     */
    fun applyRatio(src: Bitmap, ratio: PhotoRatio): Bitmap {
        if (ratio.isFullFrame) return src
        val r = ratioRect(src.width, src.height, ratio)
        if (r.left == 0 && r.top == 0 && r.width() == src.width && r.height() == src.height) return src
        return crop(src, r)
    }

    /**
     * Largest centred rect of [ratio] inside w x h, integer maths only (no float drift). The ratio is portrait
     * W:H when h >= w and landscape H:W otherwise; its aspect is exact to within 1 px on the rounded side.
     */
    fun ratioRect(w: Int, h: Int, ratio: PhotoRatio): Rect {
        val portrait = h >= w
        val rw = if (portrait) ratio.portraitW else ratio.portraitH
        val rh = if (portrait) ratio.portraitH else ratio.portraitW
        var cw = w
        var ch = ((w.toLong() * rh + rw / 2) / rw).toInt()
        if (ch > h) {
            ch = h
            cw = ((h.toLong() * rw + rh / 2) / rh).toInt()
        }
        cw = max(1, min(cw, w))
        ch = max(1, min(ch, h))
        val x = (w - cw) / 2
        val y = (h - ch) / 2
        return Rect(x, y, x + cw, y + ch)
    }

    /** Largest centred rect of width/height ratio [aspect] inside w x h. */
    private fun centered(w: Int, h: Int, aspect: Float): Rect {
        var cw = w
        var ch = (w / aspect).roundToInt()
        if (ch > h) {
            ch = h
            cw = (h * aspect).roundToInt()
        }
        cw = max(1, min(cw, w))
        ch = max(1, min(ch, h))
        val x = (w - cw) / 2
        val y = (h - ch) / 2
        return Rect(x, y, x + cw, y + ch)
    }

    private fun crop(src: Bitmap, r: Rect): Bitmap = Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())

    private fun card(photo: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val out = Bitmap.createBitmap(photo.width + left + right, photo.height + top + bottom, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(CARD_COLOR)
        canvas.drawBitmap(photo, left.toFloat(), top.toFloat(), null)
        return out
    }

    private fun halfFrame(src: Bitmap): Bitmap {
        val halfW = src.width / 2
        val aspect = 3f / 4f
        val leftRect = centered(halfW, src.height, aspect).also { it.offset(0, 0) }
        val rightRect = centered(src.width - halfW, src.height, aspect).also { it.offset(halfW, 0) }
        val fw = min(leftRect.width(), rightRect.width())
        val fh = min(leftRect.height(), rightRect.height())
        val gutter = max(2, (fw * 0.012f).roundToInt())
        val out = Bitmap.createBitmap(fw * 2 + gutter, fh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val l = Rect(leftRect.left, leftRect.top, leftRect.left + fw, leftRect.top + fh)
        val r = Rect(rightRect.left, rightRect.top, rightRect.left + fw, rightRect.top + fh)
        canvas.drawBitmap(src, l, Rect(0, 0, fw, fh), paint)
        canvas.drawBitmap(src, r, Rect(fw + gutter, 0, fw * 2 + gutter, fh), paint)
        return out
    }
}
