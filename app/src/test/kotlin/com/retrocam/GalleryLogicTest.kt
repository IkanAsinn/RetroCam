package com.retrocam

import android.net.Uri
import com.retrocam.logic.GalleryLogic
import com.retrocam.logic.LibraryPhoto
import com.retrocam.logic.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GalleryLogicTest {
    private fun photo(id: Long, millis: Long) =
        LibraryPhoto(id, Uri.parse("content://media/external/images/media/$id"), millis, "RetroCam_$id.jpg")

    @Test
    fun sortsNewestFirstWithIdTiebreak() {
        val sorted = GalleryLogic.sortNewestFirst(listOf(photo(1, 100), photo(3, 300), photo(2, 300), photo(4, 50)))
        assertEquals(listOf(3L, 2L, 1L, 4L), sorted.map { it.id })
    }

    @Test
    fun thumbnailAfterDeleteIsNextNewestOrNone() {
        val sorted = GalleryLogic.sortNewestFirst(listOf(photo(1, 100), photo(2, 200), photo(3, 300)))
        assertEquals(3L, GalleryLogic.thumbnailAfterDelete(sorted, emptySet())?.id)
        assertEquals(2L, GalleryLogic.thumbnailAfterDelete(sorted, setOf(3L))?.id)
        assertEquals(1L, GalleryLogic.thumbnailAfterDelete(sorted, setOf(3L, 2L))?.id)
        assertNull(GalleryLogic.thumbnailAfterDelete(sorted, setOf(1L, 2L, 3L)))
    }

    @Test
    fun dateFallsBackToDateAddedSeconds() {
        assertEquals(5_000L, GalleryLogic.photoMillis(5_000L, 9L))
        assertEquals(9_000L, GalleryLogic.photoMillis(0L, 9L))
    }

    @Test
    fun listingQueryOnlyMatchesRetroCamFolder() {
        val (where29, args29) = GalleryLogic.listingQuery(29, "")
        assertEquals("relative_path LIKE ?", where29)
        assertEquals(listOf("Pictures/RetroCam/%"), args29.toList())
        val (where28, args28) = GalleryLogic.listingQuery(28, "/storage/emulated/0/Pictures/")
        assertEquals("_data LIKE ?", where28)
        assertEquals(listOf("/storage/emulated/0/Pictures/RetroCam/%"), args28.toList())
    }

    @Test
    fun longPressStartsSelectionAndTapsToggle() {
        var s = Selection()
        assertFalse(s.active)
        s = s.start(5)
        assertTrue(s.active)
        s = s.toggle(7)
        assertEquals(setOf(5L, 7L), s.ids)
        assertEquals(2, s.count)
        s = s.toggle(5).toggle(7)
        assertFalse("deselecting the last photo ends selection mode", s.active)
    }

    @Test
    fun selectAllAndClear() {
        val s = Selection().selectAll(listOf(1, 2, 3))
        assertEquals(3, s.count)
        assertFalse(s.clear().active)
    }

    @Test
    fun pruneDropsDeletedIdsAndKeepsInstanceWhenUnchanged() {
        val s = Selection(setOf(1, 2, 3))
        assertSame(s, s.prune(listOf(1, 2, 3, 4)))
        assertEquals(setOf(1L, 3L), s.prune(listOf(1, 3)).ids)
        assertFalse(s.prune(emptyList()).active)
    }
}
