package com.retrocam.filmengine

import android.graphics.Bitmap

enum class FrameFormat { FULL, SQUARE, INSTANT_SQUARE, INSTANT_MINI, HALF_FRAME }

/** Post-processing on the finished Bitmap (capture path only; preview draws these as Compose overlays). */
object FrameComposer {
    fun apply(src: Bitmap, format: FrameFormat): Bitmap = TODO("phase 3")
}
