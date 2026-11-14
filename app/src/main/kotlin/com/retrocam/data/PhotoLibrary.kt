package com.retrocam.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.retrocam.logic.GalleryLogic
import com.retrocam.logic.LibraryPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.max

/** What happened to a delete request. */
sealed interface DeleteOutcome {
    /** Every requested photo is gone. */
    data object Done : DeleteOutcome

    /** The system must ask the user first: launch [sender], then call [PhotoLibrary.deleteAfterConsent] with [remaining]. */
    class NeedsConsent(val sender: IntentSender, val remaining: List<Uri>) : DeleteOutcome

    data object Failed : DeleteOutcome
}

/**
 * RetroCam's own photos in MediaStore (Pictures/RetroCam): listing, thumbnails and deleting.
 * No READ_MEDIA_* / READ_EXTERNAL_STORAGE: on API 29+ scoped storage lets an app read and delete the rows it created;
 * on API 28 WRITE_EXTERNAL_STORAGE (requested on that version only) covers reading and deleting.
 */
class PhotoLibrary(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver get() = appContext.contentResolver

    private val _changes = MutableStateFlow(0)

    /** Bumps whenever the set of photos changed (capture saved, photos deleted); collectors reload. */
    val changes: StateFlow<Int> = _changes

    fun notifyChanged() = _changes.update { it + 1 }

    private val thumbCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16L).coerceAtMost(48L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** All RetroCam photos, newest first ([limit] > 0 stops early). Throws if the query fails. */
    suspend fun list(limit: Int = 0): List<LibraryPhoto> = withContext(Dispatchers.IO) {
        val (where, args) = GalleryLogic.listingQuery(Build.VERSION.SDK_INT, picturesDir())
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val order = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
        val out = ArrayList<LibraryPhoto>()
        resolver.query(collection, projection, where, args, order)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += LibraryPhoto(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    dateMillis = GalleryLogic.photoMillis(c.getLong(takenCol), c.getLong(addedCol)),
                    name = c.getString(nameCol) ?: "",
                )
            }
        }
        val sorted = GalleryLogic.sortNewestFirst(out)
        if (limit > 0) sorted.take(limit) else sorted
    }

    /** The newest photo, or null when there is none. Throws if the query fails. */
    suspend fun newest(): Uri? = list(limit = 1).firstOrNull()?.uri

    /** A thumbnail about [sizePx] on its longest side; cached. Null if the photo cannot be read. */
    suspend fun thumbnail(uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$sizePx"
        thumbCache.get(key)?.let { return@withContext it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                decodeSampled(uri, sizePx)
            }
        } catch (_: Throwable) {
            null
        }
        if (bmp != null) thumbCache.put(key, bmp)
        bmp
    }

    /** A screen-sized decode for the full-screen viewer (not cached: only a few pages live at once). */
    suspend fun viewerBitmap(uri: Uri, maxSidePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            decodeSampled(uri, maxSidePx)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSampled(uri: Uri, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * Deletes [uris]. Rows this app created are deleted directly (no prompt). When the system refuses
     * (API 30+: SecurityException; API 29: RecoverableSecurityException) the result carries the system
     * confirmation to launch. API 28 deletes directly (WRITE_EXTERNAL_STORAGE).
     */
    suspend fun delete(uris: List<Uri>): DeleteOutcome = withContext(Dispatchers.IO) {
        val refused = ArrayList<Uri>()
        var consent: IntentSender? = null
        var failed = false
        for (uri in uris) {
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                refused += uri
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException && consent == null) {
                    consent = e.userAction.actionIntent.intentSender
                }
            } catch (_: Throwable) {
                failed = true
            }
        }
        notifyChanged()
        when {
            refused.isNotEmpty() -> {
                val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createDeleteRequest(resolver, refused).intentSender // one system dialog for the whole batch
                } else {
                    consent
                }
                if (sender != null) DeleteOutcome.NeedsConsent(sender, refused) else DeleteOutcome.Failed
            }
            failed -> DeleteOutcome.Failed
            else -> DeleteOutcome.Done
        }
    }

    /**
     * After the user approved the system dialog. API 30+: the system already deleted the whole batch.
     * API 29: the approval covers one row, so delete again (it may ask again for the next row).
     */
    suspend fun deleteAfterConsent(remaining: List<Uri>): DeleteOutcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            notifyChanged()
            return DeleteOutcome.Done
        }
        return delete(remaining)
    }

    @Suppress("DEPRECATION")
    private fun picturesDir(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "" else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
}
