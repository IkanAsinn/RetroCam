package com.retrocam.camera

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class PhotoSaverFormatTest {
    // 2026-11-13T17:05:09.007Z
    private val millis = 1_794_589_509_007L

    @Test
    fun fileNameFormat() {
        assertEquals("RetroCam_20261113_170509_007.jpg", PhotoSaver.fileName(millis, ZoneOffset.UTC))
        assertEquals("RetroCam_20261113_190509_007.jpg", PhotoSaver.fileName(millis, ZoneOffset.ofHours(2)))
    }

    @Test
    fun exifDateTimeAndOffset() {
        assertEquals("2026:11:13 17:05:09", ExifFormat.dateTime(millis, ZoneOffset.UTC))
        assertEquals("+00:00", ExifFormat.offset(millis, ZoneOffset.UTC))
        assertEquals("+05:30", ExifFormat.offset(millis, ZoneOffset.ofHoursMinutes(5, 30)))
        assertEquals("-08:00", ExifFormat.offset(millis, ZoneOffset.ofHours(-8)))
        assertEquals("007", ExifFormat.subSec(millis))
    }
}
