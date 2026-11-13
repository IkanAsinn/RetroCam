package com.retrocam.capture

import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Shutter click (MediaActionSound) and a light haptic tick. Every call is best-effort. */
class ShutterFeedback(context: Context) {
    private val appContext = context.applicationContext
    private var sound: MediaActionSound? = null

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun click(playSound: Boolean) {
        if (playSound) {
            try {
                val s = sound ?: MediaActionSound().also {
                    it.load(MediaActionSound.SHUTTER_CLICK)
                    sound = it
                }
                s.play(MediaActionSound.SHUTTER_CLICK)
            } catch (_: Throwable) {
            }
        }
        haptic()
    }

    private fun haptic() {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            sound?.release()
        } catch (_: Throwable) {
        }
        sound = null
    }
}
