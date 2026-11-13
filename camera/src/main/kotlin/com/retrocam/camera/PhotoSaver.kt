package com.retrocam.camera

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri

class PhotoSaver(context: Context) {
    /**
     * JPEG q92 to Pictures/RetroCam via MediaStore, EXIF DateTimeOriginal + Orientation NORMAL,
     * GPS only when location != null. Returns the content Uri.
     */
    suspend fun save(bitmap: Bitmap, takenAtMillis: Long, location: Location?): Uri = TODO("phase 1")

    /** RetroCam's own last photo (persisted), null if deleted. */
    suspend fun lastSaved(): Uri? = TODO("phase 1")
}
