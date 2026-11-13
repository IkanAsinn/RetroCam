package com.retrocam.filmengine

/**
 * 33x33x33 3D LUT. PNG layout is 1089x33: pixel (x = b*33 + r, y = g) holds the output
 * colour for input (r, g, b) / 32. 8-bit sRGB.
 *
 * [rgb] has size^3 * 3 entries, index ((b*size + g)*size + r)*3.
 */
class Lut(val size: Int, val rgb: ByteArray) {
    /** Trilinear CPU reference (used by tests). */
    fun sample(r: Float, g: Float, b: Float): Rgb = TODO("phase 2")

    companion object {
        fun identity(size: Int = 33): Lut = TODO("phase 2")

        /** Pure, JVM-testable. */
        fun fromStripPixels(width: Int, height: Int, argb: IntArray): Lut = TODO("phase 2")

        fun fromBitmap(bitmap: android.graphics.Bitmap): Lut = TODO("phase 2")
    }
}
