package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/** 'YY MM DD and DD MM 'YY. */
enum class DateStampFormat { YY_MM_DD, DD_MM_YY }

object DateStampRenderer {
    private const val MARGIN = 0.04f       // of the short edge
    private const val HEIGHT = 0.035f      // digit height, of the short edge
    private const val DIGIT_W = 0.56f      // of digit height
    private const val GAP = 0.30f          // between glyphs, of digit height
    private const val THICK = 0.15f        // segment thickness, of digit height

    // Segment bits: a(top) b(upper right) c(lower right) d(bottom) e(lower left) f(upper left) g(middle)
    private val DIGITS = intArrayOf(
        0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
        0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111,
    )

    /** Pure, e.g. "'26 11 13". */
    fun text(date: LocalDate, format: DateStampFormat): String {
        val yy = "'%02d".format(date.year % 100)
        val mm = "%02d".format(date.monthValue)
        val dd = "%02d".format(date.dayOfMonth)
        return when (format) {
            DateStampFormat.YY_MM_DD -> "$yy $mm $dd"
            DateStampFormat.DD_MM_YY -> "$dd $mm $yy"
        }
    }

    /**
     * Classic orange LED 7-segment stamp, bottom-right, drawn in place on a mutable [target].
     * Supports digits, space and the apostrophe (drawn as a small tick); other characters are skipped.
     */
    fun draw(target: Bitmap, text: String, color: Rgb) {
        require(target.isMutable) { "target bitmap must be mutable" }
        val shortEdge = min(target.width, target.height).toFloat()
        val h = shortEdge * HEIGHT
        val dw = h * DIGIT_W
        val gap = h * GAP
        val t = h * THICK

        fun advance(ch: Char): Float = when (ch) {
            in '0'..'9' -> dw + gap
            '\'' -> t * 1.6f + gap
            ' ' -> dw * 0.6f
            else -> 0f
        }

        val total = text.sumOf { advance(it).toDouble() }.toFloat() - gap
        val right = target.width - shortEdge * MARGIN
        val bottom = target.height - shortEdge * MARGIN
        var x = right - total
        val y = bottom - h

        val path = Path()
        for (ch in text) {
            when (ch) {
                in '0'..'9' -> addDigit(path, DIGITS[ch - '0'], x, y, dw, h, t)
                '\'' -> addTick(path, x, y, t, h)
            }
            x += advance(ch)
        }

        val argb = Color.argb(
            255,
            (color.r.coerceIn(0f, 1f) * 255f).roundToInt(),
            (color.g.coerceIn(0f, 1f) * 255f).roundToInt(),
            (color.b.coerceIn(0f, 1f) * 255f).roundToInt(),
        )
        val canvas = Canvas(target)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = argb
            alpha = 200
            maskFilter = BlurMaskFilter(max(1f, h * 0.18f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, glow)
        val crisp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = argb
        }
        canvas.drawPath(path, crisp)
    }

    private fun max(a: Float, b: Float) = if (a > b) a else b

    private fun addTick(path: Path, x: Float, y: Float, t: Float, h: Float) {
        // Slanted apostrophe tick at the top-left of the glyph slot.
        val w = t * 1.2f
        path.moveTo(x + w * 0.6f, y)
        path.lineTo(x + w * 1.6f, y)
        path.lineTo(x + w * 0.9f, y + h * 0.3f)
        path.lineTo(x, y + h * 0.3f)
        path.close()
    }

    private fun addDigit(path: Path, mask: Int, x: Float, y: Float, w: Float, h: Float, t: Float) {
        val half = t / 2f
        val gap = t * 0.12f
        val midY = y + h / 2f
        if (mask and 0b0000001 != 0) hSeg(path, x, y + half, w, t, gap)             // a
        if (mask and 0b0000010 != 0) vSeg(path, x + w - half, y + gap, midY - gap, t) // b
        if (mask and 0b0000100 != 0) vSeg(path, x + w - half, midY + gap, y + h - gap, t) // c
        if (mask and 0b0001000 != 0) hSeg(path, x, y + h - half, w, t, gap)         // d
        if (mask and 0b0010000 != 0) vSeg(path, x + half, midY + gap, y + h - gap, t) // e
        if (mask and 0b0100000 != 0) vSeg(path, x + half, y + gap, midY - gap, t)   // f
        if (mask and 0b1000000 != 0) hSeg(path, x, midY, w, t, gap)                 // g
    }

    /** Horizontal hexagonal segment centred on [yc], spanning the digit width. */
    private fun hSeg(p: Path, x: Float, yc: Float, w: Float, t: Float, gap: Float) {
        val half = t / 2f
        val x0 = x + gap
        val x1 = x + w - gap
        p.moveTo(x0, yc)
        p.lineTo(x0 + half, yc - half)
        p.lineTo(x1 - half, yc - half)
        p.lineTo(x1, yc)
        p.lineTo(x1 - half, yc + half)
        p.lineTo(x0 + half, yc + half)
        p.close()
    }

    /** Vertical hexagonal segment centred on [xc] from [y0] to [y1]. */
    private fun vSeg(p: Path, xc: Float, y0: Float, y1: Float, t: Float) {
        val half = t / 2f
        p.moveTo(xc, y0)
        p.lineTo(xc + half, y0 + half)
        p.lineTo(xc + half, y1 - half)
        p.lineTo(xc, y1)
        p.lineTo(xc - half, y1 - half)
        p.lineTo(xc - half, y0 + half)
        p.close()
    }
}
