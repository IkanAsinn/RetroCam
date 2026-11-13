package com.retrocam.profiles

import com.retrocam.filmengine.Rgb

/**
 * Parses "#RRGGBB" (also accepts "RRGGBB" and "#AARRGGBB", alpha ignored) into an [Rgb] in 0..1.
 * Pure Kotlin so it works in JVM tests.
 */
fun parseHexRgb(hex: String): Rgb {
    val s = hex.trim().removePrefix("#")
    require(s.length == 6 || s.length == 8) { "Bad colour '$hex', expected #RRGGBB" }
    val v = s.takeLast(6).toLongOrNull(16) ?: throw IllegalArgumentException("Bad colour '$hex', expected #RRGGBB")
    return Rgb(
        ((v shr 16) and 0xFF) / 255f,
        ((v shr 8) and 0xFF) / 255f,
        (v and 0xFF) / 255f,
    )
}

/** "#RRGGBB" as an opaque ARGB int, for Compose `Color(...)` / Android tinting. */
fun parseHexArgb(hex: String): Int {
    val c = parseHexRgb(hex)
    fun ch(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(c.r) shl 16) or (ch(c.g) shl 8) or ch(c.b)
}
