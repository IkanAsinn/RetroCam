package com.retrocam.logic

/** What the camera-permission screen should show. */
enum class CameraPermissionUi { GRANTED, RATIONALE, ASK_AGAIN, OPEN_SETTINGS }

object PermissionLogic {
    /**
     * [askedBefore]: the system dialog has been shown at least once.
     * [canShowSystemDialog]: `shouldShowRequestPermissionRationale` (false both before the
     * first ask and after "don't ask again", so [askedBefore] tells them apart).
     */
    fun decide(granted: Boolean, askedBefore: Boolean, canShowSystemDialog: Boolean): CameraPermissionUi = when {
        granted -> CameraPermissionUi.GRANTED
        !askedBefore -> CameraPermissionUi.RATIONALE
        canShowSystemDialog -> CameraPermissionUi.ASK_AGAIN
        else -> CameraPermissionUi.OPEN_SETTINGS
    }
}
