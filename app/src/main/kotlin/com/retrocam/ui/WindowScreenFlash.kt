package com.retrocam.ui

import android.view.Choreographer
import android.view.Window
import android.view.WindowManager
import androidx.camera.core.ImageCapture

/**
 * Front-camera flash: the screen is the light. CameraX calls [apply] only inside the capture sequence (after the
 * shutter, after any self-timer) and [clear] when it ends, so the window brightness is never touched in preview.
 *
 * [apply] sets the window brightness to full and asks for a pure white overlay ([setOverlay]); [ScreenFlashListener.onCompleted]
 * is reported on the next frame, once both are in effect. [clear] restores the exact previous brightness (including
 * "follow system", -1) and hides the overlay. Both are idempotent and main-thread only.
 */
class WindowScreenFlash(
    private val window: () -> Window?,
    private val setOverlay: (Boolean) -> Unit,
    private val afterNextFrame: (() -> Unit) -> Unit = { block -> Choreographer.getInstance().postFrameCallback { block() } },
) : ImageCapture.ScreenFlash {
    private var savedBrightness: Float? = null

    val isActive: Boolean get() = savedBrightness != null

    override fun apply(expirationTimeMillis: Long, listener: ImageCapture.ScreenFlashListener) {
        val w = window()
        if (w != null) {
            val attrs = w.attributes
            if (savedBrightness == null) savedBrightness = attrs.screenBrightness
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            w.attributes = attrs
        } else if (savedBrightness == null) {
            savedBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        setOverlay(true)
        afterNextFrame { listener.onCompleted() }
    }

    override fun clear() {
        val previous = savedBrightness ?: return
        savedBrightness = null
        window()?.let { w ->
            val attrs = w.attributes
            attrs.screenBrightness = previous
            w.attributes = attrs
        }
        setOverlay(false)
    }
}
