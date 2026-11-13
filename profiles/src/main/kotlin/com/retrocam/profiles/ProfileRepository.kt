package com.retrocam.profiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.retrocam.filmengine.Lut
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Where the repository reads its files from; the Android implementation wraps `AssetManager`. */
internal fun interface AssetSource {
    fun open(path: String): InputStream
}

/**
 * Loads camera types from `assets/profiles/<id>.json` (order from `assets/profiles/index.json`)
 * and their LUTs from `assets/luts/<id>.png`.
 *
 * JSON parsing happens once, lazily, on first access (26 tiny files, a few ms). LUT decoding
 * ([lut]) runs on [Dispatchers.IO] and is cached.
 */
class ProfileRepository internal constructor(private val assets: AssetSource) {
    constructor(context: Context) : this(
        context.applicationContext.let { app -> AssetSource { path -> app.assets.open(path) } },
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val profiles: List<CameraProfile> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ids = json.decodeFromString<List<String>>(readText("$PROFILE_DIR/index.json"))
        ids.map { id -> json.decodeFromString<CameraProfile>(readText("$PROFILE_DIR/$id.json")) }
    }

    private val byIdMap: Map<String, CameraProfile> by lazy { profiles.associateBy { it.id } }

    // Lazy so the JSON half of this class stays usable in plain JVM unit tests (android.util is stubbed there).
    private val lutCache by lazy { LruCache<String, Lut>(LUT_CACHE_SIZE) }

    /** v1.0 only by default, in PRD table order. */
    fun all(includeUnreleased: Boolean = false): List<CameraProfile> =
        if (includeUnreleased) profiles else profiles.filter { it.release == "1.0" }

    /** Looks up any profile, released or not (so a saved v1.1 selection still resolves). */
    fun byId(id: String): CameraProfile? = byIdMap[id]

    /**
     * The colour LUT of camera type [id], decoded off the main thread and cached (the 8 most
     * recently used LUTs stay in memory, ~108 KB each). Throws [IllegalArgumentException] for an unknown id.
     */
    suspend fun lut(id: String): Lut {
        lutCache.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            lutCache.get(id)?.let { return@withContext it }
            require(byId(id) != null) { "Unknown camera type '$id'" }
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = assets.open("$LUT_DIR/$id.png").use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalStateException("Cannot decode LUT for '$id'")
            val lut = try {
                Lut.fromBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
            lutCache.put(id, lut)
            lut
        }
    }

    private fun readText(path: String): String = assets.open(path).use { it.readBytes().decodeToString() }

    private companion object {
        const val PROFILE_DIR = "profiles"
        const val LUT_DIR = "luts"
        const val LUT_CACHE_SIZE = 8
    }
}
