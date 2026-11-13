package com.retrocam.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.retrocam.logic.StrengthMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "retrocam_settings")

private object Keys {
    val cameraId = stringPreferencesKey("camera_id")
    val flash = stringPreferencesKey("flash")
    val grain = stringPreferencesKey("grain")
    val flare = stringPreferencesKey("flare")
    val leaks = stringPreferencesKey("leaks")
    val dateStamp = booleanPreferencesKey("date_stamp")
    val dateFormat = stringPreferencesKey("date_format")
    val timer = stringPreferencesKey("timer")
    val shutterSound = booleanPreferencesKey("shutter_sound")
    val exifLocation = booleanPreferencesKey("exif_location")
    val zoomUnlocked = booleanPreferencesKey("zoom_unlocked")
    val facing = stringPreferencesKey("facing")
    val frameOption = booleanPreferencesKey("frame_option")
    val strengthPercent = stringPreferencesKey("strength_pct")
}

private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, default: E): E {
    val name = this[key] ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

/** Unknown or missing keys fall back to the defaults, so old or corrupt stores never crash. */
fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        cameraId = this[Keys.cameraId],
        flash = enumOr(Keys.flash, d.flash),
        grain = enumOr(Keys.grain, d.grain),
        flare = enumOr(Keys.flare, d.flare),
        leaks = enumOr(Keys.leaks, d.leaks),
        dateStamp = this[Keys.dateStamp] ?: d.dateStamp,
        dateFormat = enumOr(Keys.dateFormat, d.dateFormat),
        timer = enumOr(Keys.timer, d.timer),
        shutterSound = this[Keys.shutterSound] ?: d.shutterSound,
        exifLocation = this[Keys.exifLocation] ?: d.exifLocation,
        zoomUnlocked = this[Keys.zoomUnlocked] ?: d.zoomUnlocked,
        facing = enumOr(Keys.facing, d.facing),
        frameOption = this[Keys.frameOption] ?: d.frameOption,
        strengthPercent = StrengthMath.decode(this[Keys.strengthPercent]),
    )
}

fun MutablePreferences.writeSettings(s: Settings) {
    val id = s.cameraId
    if (id == null) remove(Keys.cameraId) else this[Keys.cameraId] = id
    this[Keys.flash] = s.flash.name
    this[Keys.grain] = s.grain.name
    this[Keys.flare] = s.flare.name
    this[Keys.leaks] = s.leaks.name
    this[Keys.dateStamp] = s.dateStamp
    this[Keys.dateFormat] = s.dateFormat.name
    this[Keys.timer] = s.timer.name
    this[Keys.shutterSound] = s.shutterSound
    this[Keys.exifLocation] = s.exifLocation
    this[Keys.zoomUnlocked] = s.zoomUnlocked
    this[Keys.facing] = s.facing.name
    this[Keys.frameOption] = s.frameOption
    val strengths = StrengthMath.encode(s.strengthPercent)
    if (strengths.isEmpty()) remove(Keys.strengthPercent) else this[Keys.strengthPercent] = strengths
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun save(settings: Settings) {
        store.edit { it.writeSettings(settings) }
    }
}
