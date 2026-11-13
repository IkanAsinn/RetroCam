package com.retrocam.filmengine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateStampTextTest {
    @Test
    fun yearMonthDay() {
        assertEquals("'26 11 13", DateStampRenderer.text(LocalDate.of(2026, 11, 13), DateStampFormat.YY_MM_DD))
    }

    @Test
    fun dayMonthYear() {
        assertEquals("13 11 '26", DateStampRenderer.text(LocalDate.of(2026, 11, 13), DateStampFormat.DD_MM_YY))
    }

    @Test
    fun padsYearsAndSingleDigitFields() {
        assertEquals("'00 01 02", DateStampRenderer.text(LocalDate.of(2000, 1, 2), DateStampFormat.YY_MM_DD))
        assertEquals("'09 03 05", DateStampRenderer.text(LocalDate.of(2009, 3, 5), DateStampFormat.YY_MM_DD))
        assertEquals("05 03 '09", DateStampRenderer.text(LocalDate.of(2009, 3, 5), DateStampFormat.DD_MM_YY))
        assertEquals("31 12 '99", DateStampRenderer.text(LocalDate.of(1999, 12, 31), DateStampFormat.DD_MM_YY))
    }
}
