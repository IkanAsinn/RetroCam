package com.retrocam.profiles

import android.content.Context
import com.retrocam.filmengine.Lut

class ProfileRepository(context: Context) {
    /** v1.0 only by default, PRD order. */
    fun all(includeUnreleased: Boolean = false): List<CameraProfile> = TODO("phase 2")

    fun byId(id: String): CameraProfile? = TODO("phase 2")

    /** Cached. */
    suspend fun lut(id: String): Lut = TODO("phase 2")
}
