package com.retrocam.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One optical lens of the back camera, positioned by its ratio to the main lens.
 *
 * - [cameraId] == null && [physicalId] == null: reached by the default back camera's zoom ratio
 *   (the main lens itself, or a lens of a logical multi-camera that the driver switches to).
 * - [physicalId]: a physical lens of the logical camera that zoom does not reach; selected with
 *   CameraSelector.Builder.setPhysicalCameraId (CameraX 1.4+), needs a rebind.
 * - [cameraId]: a lens that the phone lists as its own camera; selected with a camera-id filter,
 *   needs a rebind.
 */
internal data class LensSpec(
    val ratio: Float,
    val cameraId: String? = null,
    val physicalId: String? = null,
) {
    val separate: Boolean get() = cameraId != null || physicalId != null
}

/** Pure focal-length maths. */
object LensMath {
    /** Diagonal of a full-frame 35mm sensor in mm. */
    const val FULL_FRAME_DIAGONAL_MM = 43.2666f

    /** 35mm-equivalent focal length from the physical focal length and sensor size (mm). */
    fun equivalentFocal(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Float =
        focalMm * FULL_FRAME_DIAGONAL_MM / hypot(sensorWidthMm, sensorHeightMm)

    /** Optical ratio of a lens relative to the main lens, snapped to a human value. */
    fun ratio(lensEquivalent: Float, mainEquivalent: Float): Float =
        ZoomStops.normalizeOptical(lensEquivalent / mainEquivalent)

    /** Plausible ultra-wide / telephoto ratios only (rejects macro, depth, duplicates of the main). */
    fun isUsableRatio(r: Float): Boolean = r in 0.3f..0.9f || r in 1.4f..12f
}

@OptIn(ExperimentalCamera2Interop::class)
internal object LensDetector {
    private interface Source {
        fun <T> get(key: CameraCharacteristics.Key<T>): T?
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private class InfoSource(val info: CameraInfo) : Source {
        override fun <T> get(key: CameraCharacteristics.Key<T>): T? =
            Camera2CameraInfo.from(info).getCameraCharacteristic(key)
    }

    private class CharsSource(val chars: CameraCharacteristics) : Source {
        override fun <T> get(key: CameraCharacteristics.Key<T>): T? = chars.get(key)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun cameraId(info: CameraInfo): String? = try {
        Camera2CameraInfo.from(info).cameraId
    } catch (_: Throwable) {
        null
    }

    /** CameraSelector for [lens] on the back camera. */
    fun backSelector(lens: LensSpec): CameraSelector {
        val b = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
        lens.cameraId?.let { id ->
            b.addCameraFilter { infos -> infos.filter { cameraId(it) == id } }
        }
        lens.physicalId?.let { b.setPhysicalCameraId(it) }
        return b.build()
    }

    /**
     * Lenses of the back camera, ascending by ratio, main (1.0) included. [main] is the bound
     * default back camera; [mainMin]/[mainMax] its zoom range; [all] every camera CameraX lists.
     * Never throws: on any failure the result is just the main lens.
     */
    fun detectBack(
        context: Context,
        main: CameraInfo,
        mainMin: Float,
        mainMax: Float,
        all: List<CameraInfo>,
    ): List<LensSpec> {
        val mainLens = LensSpec(1f)
        return try {
            val mainSource = InfoSource(main)
            val mainEq = equivalentFocal(mainSource) ?: return listOf(mainLens)
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val zoomByRatio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val mainId = cameraId(main)
            val seen = HashSet<String>().apply { mainId?.let { add(it) } }
            val found = ArrayList<LensSpec>()

            // 1. Physical lenses of the logical multi-camera.
            val physicalIds: Set<String> = try {
                mainId?.let { manager?.getCameraCharacteristics(it)?.physicalCameraIds }.orEmpty()
            } catch (_: Throwable) {
                emptySet()
            }
            for (pid in physicalIds) {
                seen += pid
                val src = physicalSource(manager, main, pid) ?: continue
                val eq = equivalentFocal(src) ?: continue
                val ratio = LensMath.ratio(eq, mainEq)
                if (!LensMath.isUsableRatio(ratio)) continue
                val reachable = zoomByRatio && (if (ratio < 1f) mainMin <= ratio + 0.15f else ratio <= mainMax)
                found += if (reachable) LensSpec(ratio) else LensSpec(ratio, physicalId = pid)
            }

            // 2. Lenses the phone lists as separate cameras.
            for (info in all) {
                if (info.lensFacing != CameraSelector.LENS_FACING_BACK) continue
                val id = cameraId(info) ?: continue
                if (!seen.add(id)) continue
                val eq = equivalentFocal(InfoSource(info)) ?: continue
                val ratio = LensMath.ratio(eq, mainEq)
                if (!LensMath.isUsableRatio(ratio)) continue
                found += LensSpec(ratio, cameraId = id)
            }

            // Dedupe near-identical ratios, preferring lenses reachable by zoom.
            val result = ArrayList<LensSpec>().apply { add(mainLens) }
            for (l in found.sortedBy { it.separate }) {
                if (result.none { abs(it.ratio - l.ratio) < 0.08f }) result += l
            }
            result.sortedBy { it.ratio }
        } catch (_: Throwable) {
            listOf(mainLens)
        }
    }

    private fun equivalentFocal(src: Source): Float? {
        val focals = src.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val size = src.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val f = focals?.firstOrNull() ?: return null
        if (size == null || size.width <= 0f || size.height <= 0f || f <= 0f) return null
        return LensMath.equivalentFocal(f, size.width, size.height)
    }

    private fun physicalSource(manager: CameraManager?, main: CameraInfo, id: String): Source? {
        try {
            if (manager != null) return CharsSource(manager.getCameraCharacteristics(id))
        } catch (_: Throwable) {
            // physical id not queryable through CameraManager on this device; try CameraX
        }
        return try {
            main.physicalCameraInfos.firstOrNull { cameraId(it) == id }?.let { InfoSource(it) }
        } catch (_: Throwable) {
            null
        }
    }
}
