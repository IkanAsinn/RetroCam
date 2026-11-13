package com.retrocam.logic

import com.retrocam.camera.ZoomStop
import com.retrocam.filmengine.Rgb
import kotlin.math.abs

object UiMath {
    /** Index of the zoom stop closest to [ratio], or -1 when there are none. */
    fun selectedStop(stops: List<ZoomStop>, ratio: Float): Int =
        stops.indices.minByOrNull { abs(stops[it].ratio - ratio) } ?: -1

    /** "#RRGGBB" -> [Rgb] 0..1; [fallback] (classic date-stamp orange) when malformed. */
    fun hexToRgb(hex: String, fallback: Rgb = Rgb(0.98f, 0.55f, 0.12f)): Rgb {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return fallback
        val v = h.toIntOrNull(16) ?: return fallback
        return Rgb(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
    }
}
