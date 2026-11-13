package com.retrocam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Saves finished photos to Pictures/RetroCam. All work runs on Dispatchers.IO.
 *
 * API 29+: MediaStore insert with RELATIVE_PATH and IS_PENDING (no storage permission).
 * API 28: file written to the public Pictures/RetroCam directory (needs WRITE_EXTERNAL_STORAGE,
 * declared with maxSdkVersion 28) and registered in MediaStore with its DATA path.
 */
class PhotoSaver(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * JPEG q92 to Pictures/RetroCam via MediaStore, EXIF DateTimeOriginal + Orientation NORMAL,
     * GPS only when location != null. Returns the content Uri.
     */
    suspend fun save(bitmap: Bitmap, takenAtMillis: Long, location: Location?): Uri =
        withContext(Dispatchers.IO) {
            val name = fileName(takenAtMillis)
            val temp = File(appContext.cacheDir, name)
            try {
                temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) { "JPEG compress failed" } }
                writeExif(temp, takenAtMillis, location)
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    insertScoped(temp, name, takenAtMillis)
                } else {
                    insertLegacy(temp, name, takenAtMillis)
                }
                prefs.edit().putString(KEY_LAST_URI, uri.toString()).apply()
                uri
            } finally {
                temp.delete()
            }
        }

    /** RetroCam's own last photo (persisted), null if deleted. */
    suspend fun lastSaved(): Uri? = withContext(Dispatchers.IO) {
        val uri = prefs.getString(KEY_LAST_URI, null)?.let(Uri::parse) ?: return@withContext null
        val exists = try {
            appContext.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!exists) {
            prefs.edit().remove(KEY_LAST_URI).apply()
            null
        } else {
            uri
        }
    }

    private fun writeExif(file: File, takenAtMillis: Long, location: Location?) {
        val zone = ZoneId.systemDefault()
        val dateTime = ExifFormat.dateTime(takenAtMillis, zone)
        val offset = ExifFormat.offset(takenAtMillis, zone)
        val subSec = ExifFormat.subSec(takenAtMillis)
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subSec)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, subSec)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subSec)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, SOFTWARE)
        if (location != null) exif.setGpsInfo(location)
        exif.saveAttributes()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun insertScoped(temp: File, name: String, takenAtMillis: Long): Uri {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAtMillis)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri")
            out.use { o -> temp.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun insertLegacy(temp: File, name: String, takenAtMillis: Long): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIR_NAME)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) throw IOException("Cannot create $dir")
        val target = File(dir, name)
        temp.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAtMillis)
            put(MediaStore.Images.Media.SIZE, target.length())
            put(MediaStore.Images.Media.DATA, target.absolutePath)
        }
        val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            // Row insert failed but the file exists: let the scanner register it.
            MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(MIME_JPEG), null)
            throw IOException("MediaStore insert failed")
        }
        return uri
    }

    companion object {
        const val JPEG_QUALITY = 92
        const val DIR_NAME = "RetroCam"
        private const val RELATIVE_PATH = "Pictures/RetroCam"
        private const val MIME_JPEG = "image/jpeg"
        private const val SOFTWARE = "RetroCam"
        private const val PREFS = "retrocam_photos"
        private const val KEY_LAST_URI = "last_uri"
        private val FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)

        /** RetroCam_yyyyMMdd_HHmmss_SSS.jpg */
        fun fileName(takenAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            "RetroCam_" + FILE_FORMAT.format(Instant.ofEpochMilli(takenAtMillis).atZone(zone)) + ".jpg"
    }
}

/** Pure EXIF string formatting. */
object ExifFormat {
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

    /** "yyyy:MM:dd HH:mm:ss" in [zone]. */
    fun dateTime(millis: Long, zone: ZoneId): String = DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "+HH:MM" UTC offset in effect at [millis]. */
    fun offset(millis: Long, zone: ZoneId): String {
        val total = zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds
        val sign = if (total < 0) '-' else '+'
        val abs = kotlin.math.abs(total)
        return String.format(Locale.ROOT, "%c%02d:%02d", sign, abs / 3600, abs % 3600 / 60)
    }

    /** Sub-second digits ("007") for the SubSecTime tags. */
    fun subSec(millis: Long): String = String.format(Locale.ROOT, "%03d", Math.floorMod(millis, 1000L))
}
