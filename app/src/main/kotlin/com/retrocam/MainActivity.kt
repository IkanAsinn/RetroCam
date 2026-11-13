package com.retrocam

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.retrocam.logic.CameraPermissionUi
import com.retrocam.logic.PermissionLogic
import com.retrocam.ui.CameraScreen
import com.retrocam.ui.PermissionScreen
import com.retrocam.ui.openAppSettings
import com.retrocam.ui.theme.RetroCamTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as RetroCamApp).container
    private val viewModel: CameraViewModel by viewModels { CameraViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            RetroCamTheme {
                Root()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { container.orientationWatcher.start() }
    }

    override fun onStop() {
        runCatching { container.orientationWatcher.stop() }
        super.onStop()
    }

    // Volume keys are a second shutter (PRD accessibility).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.repeatCount == 0) viewModel.onShutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true
        return super.onKeyUp(keyCode, event)
    }

    @Composable
    private fun Root() {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasCamera()) }
        var asked by rememberSaveable { mutableStateOf(false) }
        var canShowDialog by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            asked = true
            granted = result[Manifest.permission.CAMERA] == true || hasCamera()
            canShowDialog = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
        }
        // Back from the system settings page: pick up a permission granted there.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera() }

        val mode = PermissionLogic.decide(granted, asked, canShowDialog)
        if (mode == CameraPermissionUi.GRANTED) {
            CameraScreen(
                viewModel = viewModel,
                crashLogger = container.crashLogger,
                hasLocationPermission = { container.locationProvider.hasPermission() },
            )
        } else {
            PermissionScreen(
                mode = mode,
                onAllow = { launcher.launch(requiredPermissions()) },
                onOpenSettings = { openAppSettings(context) },
            )
        }
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** CAMERA everywhere; API 28 also needs WRITE_EXTERNAL_STORAGE for MediaStore writes to Pictures/RetroCam. */
    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
}
