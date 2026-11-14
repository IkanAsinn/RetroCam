package com.retrocam

import android.app.Activity
import android.view.WindowManager
import androidx.camera.core.ImageCapture
import com.retrocam.ui.WindowScreenFlash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenFlashTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val window get() = activity.window
    private var overlay = false
    private var completed = 0
    private val listener = ImageCapture.ScreenFlashListener { completed++ }
    private val pending = mutableListOf<() -> Unit>()

    private fun flash() = WindowScreenFlash({ window }, { overlay = it }, { pending += it })

    private fun setBrightness(v: Float) {
        window.attributes = window.attributes.also { it.screenBrightness = v }
    }

    @Test
    fun nothingHappensUntilCameraXAppliesIt() {
        setBrightness(0.3f)
        flash()
        assertEquals(0.3f, window.attributes.screenBrightness, 0f)
        assertFalse(overlay)
    }

    @Test
    fun applyRaisesBrightnessShowsOverlayAndCompletesOnNextFrame() {
        val f = flash()
        f.apply(1000, listener)
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL, window.attributes.screenBrightness, 0f)
        assertTrue(overlay)
        assertEquals("completion waits for the next frame", 0, completed)
        pending.forEach { it() }
        assertEquals(1, completed)
    }

    @Test
    fun clearRestoresTheExactPreviousBrightness() {
        setBrightness(0.37f)
        val f = flash()
        f.apply(1000, listener)
        f.apply(1000, listener) // a second apply must not overwrite the saved value with FULL
        f.clear()
        assertEquals(0.37f, window.attributes.screenBrightness, 0f)
        assertFalse(overlay)
        f.clear() // idempotent
        assertEquals(0.37f, window.attributes.screenBrightness, 0f)
    }

    @Test
    fun followSystemBrightnessIsRestoredAsFollowSystem() {
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        val f = flash()
        f.apply(1000, listener)
        f.clear()
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, window.attributes.screenBrightness, 0f)
    }
}
