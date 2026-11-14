package com.retrocam

import android.app.Application
import com.retrocam.camera.OrientationWatcher
import com.retrocam.camera.PhotoSaver
import com.retrocam.camera.RetroCameraController
import com.retrocam.capture.CapturePipeline
import com.retrocam.capture.LocationProvider
import com.retrocam.capture.ShutterFeedback
import com.retrocam.data.PhotoLibrary
import com.retrocam.data.SettingsRepository
import com.retrocam.filmengine.OffscreenFilmRenderer
import com.retrocam.logic.ReleasableHolder
import com.retrocam.profiles.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual constructor injection: everything lazily built once, so cold start only pays for what it shows. */
class AppContainer(private val app: Application) {
    /** Outlives the activity: in-flight photos keep saving after the user leaves the screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val crashLogger = CrashLogger(app)
    val settingsRepository by lazy { SettingsRepository(app) }
    val profileRepository by lazy { ProfileRepository(app) }
    val controller by lazy { RetroCameraController(app) }
    val photoSaver by lazy { PhotoSaver(app) }
    val photoLibrary by lazy { PhotoLibrary(app) }
    val orientationWatcher by lazy { OrientationWatcher(app) }
    val locationProvider by lazy { LocationProvider(app) }
    val shutterFeedback by lazy { ShutterFeedback(app) }

    /** The GL renderer is created on first capture and released once the screen is gone and no save is running. */
    val renderer = ReleasableHolder(create = { OffscreenFilmRenderer() }, release = { it.release() })

    val capturePipeline by lazy { CapturePipeline(profileRepository, renderer, photoSaver) }
}

class RetroCamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashLogger.install()
    }
}
