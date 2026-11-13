package com.retrocam.filmengine

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 33x33x33 3D LUT. PNG layout is 1089x33: pixel (x = b*33 + r, y = g) holds the output
 * colour for input (r, g, b) / 32. 8-bit sRGB.
 *
 * [rgb] has size^3 * 3 entries, index ((b*size + g)*size + r)*3.
 */
class Lut(val size: Int, val rgb: ByteArray) {
    init {
        require(size >= 2) { "LUT size must be >= 2, was $size" }
        require(rgb.size == size * size * size * 3) {
            "LUT data must have size^3*3 = ${size * size * size * 3} bytes, was ${rgb.size}"
        }
    }

    /** Index of the red byte of lattice point (r, g, b). */
    fun index(r: Int, g: Int, b: Int): Int = ((b * size + g) * size + r) * 3

    /** Trilinear CPU reference (used by tests). Inputs are clamped to 0..1. */
    fun sample(r: Float, g: Float, b: Float): Rgb {
        val n = size - 1
        val fr = r.coerceIn(0f, 1f) * n
        val fg = g.coerceIn(0f, 1f) * n
        val fb = b.coerceIn(0f, 1f) * n
        val r0 = min(floor(fr).toInt(), n)
        val g0 = min(floor(fg).toInt(), n)
        val b0 = min(floor(fb).toInt(), n)
        val r1 = min(r0 + 1, n)
        val g1 = min(g0 + 1, n)
        val b1 = min(b0 + 1, n)
        val tr = fr - r0
        val tg = fg - g0
        val tb = fb - b0
        val out = FloatArray(3)
        for (ch in 0..2) {
            val c000 = at(r0, g0, b0, ch)
            val c100 = at(r1, g0, b0, ch)
            val c010 = at(r0, g1, b0, ch)
            val c110 = at(r1, g1, b0, ch)
            val c001 = at(r0, g0, b1, ch)
            val c101 = at(r1, g0, b1, ch)
            val c011 = at(r0, g1, b1, ch)
            val c111 = at(r1, g1, b1, ch)
            val x00 = c000 + (c100 - c000) * tr
            val x10 = c010 + (c110 - c010) * tr
            val x01 = c001 + (c101 - c001) * tr
            val x11 = c011 + (c111 - c011) * tr
            val y0 = x00 + (x10 - x00) * tg
            val y1 = x01 + (x11 - x01) * tg
            out[ch] = (y0 + (y1 - y0) * tb) / 255f
        }
        return Rgb(out[0], out[1], out[2])
    }

    private fun at(r: Int, g: Int, b: Int, ch: Int): Float = (rgb[index(r, g, b) + ch].toInt() and 0xFF).toFloat()

    /** Encodes this LUT into the strip layout (width = size*size, height = size), opaque ARGB. */
    fun toStripPixels(): IntArray {
        val width = size * size
        val out = IntArray(width * size)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val i = index(r, g, b)
            val rr = rgb[i].toInt() and 0xFF
            val gg = rgb[i + 1].toInt() and 0xFF
            val bb = rgb[i + 2].toInt() and 0xFF
            out[g * width + b * size + r] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        return out
    }

    companion object {
        fun identity(size: Int = 33): Lut {
            val data = ByteArray(size * size * size * 3)
            val steps = IntArray(size) { (it * 255f / (size - 1)).roundToInt() }
            var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                data[i++] = steps[r].toByte()
                data[i++] = steps[g].toByte()
                data[i++] = steps[b].toByte()
            }
            return Lut(size, data)
        }

        /** Pure, JVM-testable. Strip is (size*size) wide and size high. */
        fun fromStripPixels(width: Int, height: Int, argb: IntArray): Lut {
            val size = height
            require(size >= 2 && width == size * size) {
                "LUT strip must be (size*size) x size, was ${width}x$height"
            }
            require(argb.size >= width * height) { "Pixel array too small for ${width}x$height" }
            val data = ByteArray(size * size * size * 3)
            var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val p = argb[g * width + b * size + r]
                data[i++] = (p shr 16).toByte()
                data[i++] = (p shr 8).toByte()
                data[i++] = p.toByte()
            }
            return Lut(size, data)
        }

        fun fromBitmap(bitmap: Bitmap): Lut {
            val w = bitmap.width
            val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            return fromStripPixels(w, h, px)
        }
    }
}
