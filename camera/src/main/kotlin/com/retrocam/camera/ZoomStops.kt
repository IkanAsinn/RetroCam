package com.retrocam.camera

import java.util.Locale
import kotlin.math.roundToInt

/** Pure zoom-stop maths: chips, zoom limit and lens classification. No Android types. */
object ZoomStops {
    /** Digital zoom allowed on single-lens phones while full zoom is locked. */
    const val DEFAULT_DIGITAL_LIMIT = 2f

    private const val RANGE_TOLERANCE_LOW = 0.15f
    private const val RANGE_TOLERANCE_HIGH = 0.05f

    /**
     * Chips for the lens row. Only stops inside the device range (and inside the zoom limit),
     * always including 1x, ascending, no duplicates. Single-lens phones also get a 2x digital stop.
     *
     * [opticalRatios] are 35mm-equivalent ratios relative to the main lens (e.g. 0.6, 2, 3); the
     * main lens (1.0) is implied and need not be listed.
     */
    fun compute(minZoom: Float, maxZoom: Float, opticalRatios: List<Float>, unlocked: Boolean): List<ZoomStop> {
        val limit = maxAllowed(minZoom, maxZoom, opticalRatios, unlocked)
        val optical = opticalSet(opticalRatios)
        val candidates = ArrayList<Float>()
        for (r in optical) {
            if (r == 1f || (r >= minZoom - RANGE_TOLERANCE_LOW && r <= limit + RANGE_TOLERANCE_HIGH)) {
                candidates += r.coerceIn(minZoom, maxOf(minZoom, limit))
            }
        }
        val singleLens = optical.none { it > 1.05f }
        if (singleLens && limit >= DEFAULT_DIGITAL_LIMIT - 0.01f) candidates += DEFAULT_DIGITAL_LIMIT
        return candidates
            .map { roundTenth(it) }
            .distinct()
            .sorted()
            .map { ZoomStop(it, label(it)) }
    }

    /**
     * Highest zoom ratio the user may reach. Unlocked: the device maximum. Locked: the longest
     * optical lens, or [DEFAULT_DIGITAL_LIMIT] on single-lens phones; never above [maxZoom].
     */
    fun maxAllowed(minZoom: Float, maxZoom: Float, opticalRatios: List<Float>, unlocked: Boolean): Float {
        if (unlocked) return maxZoom
        val longest = opticalSet(opticalRatios).max()
        val limit = if (longest > 1.05f) longest else DEFAULT_DIGITAL_LIMIT
        return minOf(limit, maxZoom).coerceAtLeast(minZoom)
    }

    /** "0.6×", "1×", "2.5×": one decimal, trailing ".0" dropped. */
    fun label(ratio: Float): String {
        val tenths = (ratio * 10f).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}×" else String.format(Locale.ROOT, "%.1f×", tenths / 10f)
    }

    /** Snap a measured optical ratio to something a person would call it (0.5, 0.6, 1, 2, 2.5, 3, 5). */
    fun normalizeOptical(raw: Float): Float = when {
        raw in 0.95f..1.05f -> 1f
        raw >= 1.4f -> (raw * 2f).roundToInt() / 2f
        else -> roundTenth(raw)
    }

    /**
     * Which lens is delivering the image at [zoomRatio]. Below ~1x the ultra-wide is active; at or
     * above the first telephoto ratio the telephoto is active; otherwise the main lens.
     * (The front camera is decided by the caller.)
     */
    fun lensKind(zoomRatio: Float, opticalRatios: List<Float>): ActiveLens {
        if (zoomRatio < 0.95f) return ActiveLens.ULTRA_WIDE
        val tele = opticalSet(opticalRatios).filter { it >= 1.4f }.minOrNull()
        if (tele != null && zoomRatio >= tele * 0.97f) return ActiveLens.TELE
        return ActiveLens.MAIN
    }

    private fun opticalSet(raw: List<Float>): List<Float> =
        (raw.map { normalizeOptical(it) } + 1f).distinctBy { (it * 100).roundToInt() }.sorted()

    private fun roundTenth(v: Float): Float = (v * 10f).roundToInt() / 10f
}
