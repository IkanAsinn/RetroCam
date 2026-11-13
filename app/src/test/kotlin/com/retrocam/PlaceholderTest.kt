package com.retrocam

import com.retrocam.ui.theme.RetroColors
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaceholderTest {
    @Test
    fun accentIsNotBackground() {
        assertNotEquals(RetroColors.Orange, RetroColors.Charcoal)
    }
}
