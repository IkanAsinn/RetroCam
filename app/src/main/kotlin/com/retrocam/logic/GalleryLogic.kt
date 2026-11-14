package com.retrocam.logic

import android.net.Uri

/** One RetroCam photo as listed from MediaStore. [dateMillis] is the time it was taken (or added). */
data class LibraryPhoto(val id: Long, val uri: Uri, val dateMillis: Long, val name: String)

/** Pure gallery rules, kept free of Android services so they unit-test without a device. */
object GalleryLogic {
    /** Newest first; equal timestamps (burst shots) fall back to the higher MediaStore id. */
    fun sortNewestFirst(photos: List<LibraryPhoto>): List<LibraryPhoto> =
        photos.sortedWith(compareByDescending<LibraryPhoto> { it.dateMillis }.thenByDescending { it.id })

    /** The photo the camera screen's thumbnail should show once [deleted] are gone ([sorted] is newest first). */
    fun thumbnailAfterDelete(sorted: List<LibraryPhoto>, deleted: Set<Long>): LibraryPhoto? =
        sorted.firstOrNull { it.id !in deleted }

    /** Date in millis: DATE_TAKEN when present, else DATE_ADDED (seconds). */
    fun photoMillis(dateTaken: Long, dateAddedSeconds: Long): Long =
        if (dateTaken > 0) dateTaken else dateAddedSeconds * 1000L

    /** MediaStore WHERE clause and args listing only Pictures/RetroCam. [picturesDir] is only used below API 29. */
    fun listingQuery(sdk: Int, picturesDir: String): Pair<String, Array<String>> =
        if (sdk >= 29) {
            "relative_path LIKE ?" to arrayOf("Pictures/RetroCam/%")
        } else {
            "_data LIKE ?" to arrayOf(picturesDir.trimEnd('/') + "/RetroCam/%")
        }
}

/**
 * Multi-select state. Selecting is active exactly while at least one photo is selected:
 * a long-press starts it, tapping the last selected photo again ends it.
 */
data class Selection(val ids: Set<Long> = emptySet()) {
    val active: Boolean get() = ids.isNotEmpty()
    val count: Int get() = ids.size

    fun start(id: Long) = Selection(setOf(id))
    fun toggle(id: Long) = Selection(if (id in ids) ids - id else ids + id)
    fun selectAll(all: Collection<Long>) = Selection(all.toSet())
    fun clear() = Selection()

    /** Drops ids that no longer exist (after a delete or refresh). */
    fun prune(existing: Collection<Long>): Selection {
        val keep = ids.intersect(existing.toSet())
        return if (keep.size == ids.size) this else Selection(keep)
    }
}
