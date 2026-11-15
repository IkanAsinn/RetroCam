package com.retrocam.data

import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.filmengine.DateStampFormat
import com.retrocam.filmengine.PhotoRatio
import com.retrocam.logic.StrengthMath
import com.retrocam.profiles.EffectToggles
import com.retrocam.profiles.FlareMode
import com.retrocam.profiles.GrainLevel
import com.retrocam.profiles.LeakMode

/** Self-timer choices; [seconds] is the countdown length. */
enum class TimerSetting(val seconds: Int) { OFF(0), THREE(3), TEN(10) }

/** Everything the user can change and expect to find again next launch (PRD F17). Pure data. */
data class Settings(
    val cameraId: String? = null,
    val flash: FlashMode = FlashMode.OFF,
    val filmFlash: Boolean = false,            // independent of the flash mode: adds the simulated flash at render time
    val grain: GrainLevel = GrainLevel.NORMAL,
    val flare: FlareMode = FlareMode.AUTO,
    val leaks: LeakMode = LeakMode.OFF,
    val dateStamp: Boolean = false,            // PRD hard rule: off by default
    val dateFormat: DateStampFormat = DateStampFormat.YY_MM_DD,
    val timer: TimerSetting = TimerSetting.OFF,
    val shutterSound: Boolean = true,
    val exifLocation: Boolean = false,         // opt-in
    val zoomUnlocked: Boolean = false,
    val facing: Facing = Facing.BACK,
    val frameOption: Boolean = true,           // white border for instant types
    val ratio: PhotoRatio = PhotoRatio.R4_3,   // applies to FULL-format cameras only; remembered for fixed-format ones
    val mirrorFront: Boolean = false,          // save front-camera photos mirrored like the viewfinder
    val strengthPercent: Map<String, Int> = emptyMap(), // profile id -> filter strength percent; missing = 100
) {
    val toggles: EffectToggles get() = EffectToggles(grain, flare, leaks)

    /** Strength percent (0..100) for [profileId]; 100 when never changed. */
    fun strengthFor(profileId: String?): Int =
        profileId?.let { strengthPercent[it] }?.coerceIn(0, StrengthMath.FULL) ?: StrengthMath.FULL

    /** Copy with [profileId]'s strength set; 100 removes the entry so the store stays small. */
    fun withStrength(profileId: String, percent: Int): Settings {
        val p = percent.coerceIn(0, StrengthMath.FULL)
        val next = if (p == StrengthMath.FULL) strengthPercent - profileId else strengthPercent + (profileId to p)
        return if (next == strengthPercent) this else copy(strengthPercent = next)
    }
}

/** Next value in declaration order, wrapping around. This is the tap-to-cycle order of every top-bar toggle. */
inline fun <reified E : Enum<E>> E.cycleNext(): E {
    val all = enumValues<E>()
    return all[(ordinal + 1) % all.size]
}
