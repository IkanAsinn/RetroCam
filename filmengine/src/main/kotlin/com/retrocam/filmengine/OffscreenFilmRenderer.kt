package com.retrocam.filmengine

import android.graphics.Bitmap

/**
 * Capture path: the SAME shader on an EGL pbuffer on its own GL thread. Tiles inputs
 * larger than GL_MAX_TEXTURE_SIZE. Output has the input's dimensions.
 */
class OffscreenFilmRenderer {
    suspend fun render(input: Bitmap, params: FilmParams, lut: Lut): Bitmap = TODO("phase 2")

    fun release(): Unit = TODO("phase 2")
}
