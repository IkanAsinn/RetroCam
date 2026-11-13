package com.retrocam.logic

/** Filter-strength helpers: the UI and storage speak whole percent, the shader speaks 0..1. */
object StrengthMath {
    const val FULL = 100
    const val STEP = 5

    /** Percent -> shader strength 0..1, clamped (anything outside 0..100 is limited). */
    fun toStrength(percent: Int): Float = percent.coerceIn(0, FULL) / 100f

    /** Clamp to 0..100 and round to the nearest [STEP]. */
    fun snap(percent: Float): Int = ((percent / STEP).let { Math.round(it) } * STEP).coerceIn(0, FULL)

    /** Stored form: only non-default entries, "id:pct;id:pct". */
    fun encode(map: Map<String, Int>): String =
        map.entries.filter { it.value.coerceIn(0, FULL) != FULL && it.key.isNotEmpty() }
            .joinToString(";") { "${it.key}:${it.value.coerceIn(0, FULL)}" }

    /** Tolerant inverse of [encode]: malformed entries are skipped, values clamped, 100 dropped. */
    fun decode(text: String?): Map<String, Int> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (part in text.split(';')) {
            val i = part.lastIndexOf(':')
            if (i <= 0) continue
            val pct = part.substring(i + 1).trim().toIntOrNull() ?: continue
            val v = pct.coerceIn(0, FULL)
            if (v != FULL) out[part.substring(0, i)] = v
        }
        return out
    }
}
