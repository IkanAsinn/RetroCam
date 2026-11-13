package com.retrocam.filmengine

import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest

/** Preview path: a CameraX CameraEffect (target PREVIEW) wrapping a GL SurfaceProcessor. */
class FilmCameraEffect(errorListener: (Throwable) -> Unit) : CameraEffect(
    PREVIEW,
    Runnable::run,
    StubSurfaceProcessor,
    errorListener,
) {
    /** Thread-safe, may be called every frame. */
    fun update(params: FilmParams, lut: Lut): Unit = TODO("phase 2")

    fun release(): Unit = TODO("phase 2")
}

// Placeholder until the GL SurfaceProcessor lands in phase 2.
private object StubSurfaceProcessor : SurfaceProcessor {
    override fun onInputSurface(request: SurfaceRequest): Unit = TODO("phase 2")
    override fun onOutputSurface(surfaceOutput: SurfaceOutput): Unit = TODO("phase 2")
}
