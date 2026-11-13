package com.retrocam.filmengine

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt

/** Brightest region of the preview. [x], [y] in 0..1 UV of the upright frame (origin top-left). */
data class Highlight(val x: Float, val y: Float, val luma: Float)

/**
 * Preview path: a CameraX CameraEffect (target PREVIEW) wrapping a GL SurfaceProcessor.
 *
 * Runs on its own GL thread. [update] may be called from any thread, as often as every frame.
 * The effect is reusable across camera (re)binds; call [release] once when it is no longer needed.
 */
class FilmCameraEffect private constructor(
    private val processor: FilmSurfaceProcessor,
    errorListener: (Throwable) -> Unit,
) : CameraEffect(PREVIEW, Runnable::run, processor, errorListener) {

    constructor(errorListener: (Throwable) -> Unit) : this(FilmSurfaceProcessor(errorListener), errorListener)

    /** Thread-safe, may be called every frame. The LUT is re-uploaded only when the instance changes. */
    fun update(params: FilmParams, lut: Lut) {
        processor.update(params, lut)
    }

    /**
     * Brightest region of the (unfiltered) preview, sampled every ~10 frames from a small
     * downscaled read-back. null = nothing bright.
     */
    val highlight: StateFlow<Highlight?> get() = processor.highlight

    fun release() {
        processor.release()
    }
}

private class FilmState(val params: FilmParams, val lut: Lut)

private class InputSlot(
    val texId: Int,
    val surfaceTexture: SurfaceTexture,
    val surface: Surface,
) {
    var closed = false
}

private class OutputSlot(
    val output: SurfaceOutput,
    val eglSurface: EGLSurface,
    val size: Size,
) {
    val matrix = FloatArray(16)
}

private class FilmSurfaceProcessor(private val onError: (Throwable) -> Unit) : SurfaceProcessor {
    private val thread = HandlerThread("RetroCam-FilmGL").also { it.start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val released = AtomicBoolean(false)
    private val state = AtomicReference(FilmState(FilmParams(), Lut.identity()))
    private val _highlight = MutableStateFlow<Highlight?>(null)
    val highlight: StateFlow<Highlight?> get() = _highlight

    // ---- GL thread only ----
    private var egl: EglCore? = null
    private var scratch: EGLSurface? = null
    private var program: FilmProgram? = null
    private var passthrough: PassthroughProgram? = null
    private var quad: FullscreenQuad? = null
    private var lutTex = 0
    private var lutUploaded: Lut? = null
    private val inputs = ArrayList<InputSlot>()
    private val outputs = ArrayList<OutputSlot>()
    private val texMatrix = FloatArray(16)
    private var frame = 0
    private var failed = false
    private var hlFbo = 0
    private var hlTex = 0
    private var hlW = 0
    private var hlH = 0
    private var hlBuf: ByteBuffer? = null

    fun update(params: FilmParams, lut: Lut) {
        state.set(FilmState(params, lut))
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        val posted = handler.post {
            try {
                teardown()
            } finally {
                thread.quitSafely()
            }
        }
        if (!posted) thread.quitSafely()
    }

    // ---- SurfaceProcessor ----

    override fun onInputSurface(request: SurfaceRequest) {
        if (released.get() || !handler.post { setupInput(request) }) {
            request.willNotProvideSurface()
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released.get() || !handler.post { setupOutput(surfaceOutput) }) {
            surfaceOutput.close()
        }
    }

    // ---- setup ----

    private fun ensureGl() {
        if (egl != null) return
        val core = EglCore()
        try {
            val pb = core.createPbuffer(1, 1)
            core.makeCurrent(pb)
            egl = core
            scratch = pb
            program = FilmProgram(external = true)
            passthrough = PassthroughProgram(external = true)
            quad = FullscreenQuad()
            GlUtil.checkError("preview init")
        } catch (t: Throwable) {
            teardown()
            if (egl == null) core.release()
            throw t
        }
    }

    private fun setupInput(request: SurfaceRequest) {
        if (released.get()) {
            request.willNotProvideSurface()
            return
        }
        try {
            ensureGl()
            egl!!.makeCurrent(scratch!!)
            val tex = GlUtil.createExternalTexture()
            val st = SurfaceTexture(tex)
            st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            val surface = Surface(st)
            val slot = InputSlot(tex, st, surface)
            inputs.add(slot)
            st.setOnFrameAvailableListener({ onFrame(slot) }, handler)
            request.provideSurface(surface, executor) { teardownInput(slot) }
        } catch (t: Throwable) {
            request.willNotProvideSurface()
            reportError(t)
        }
    }

    private fun setupOutput(surfaceOutput: SurfaceOutput) {
        if (released.get()) {
            surfaceOutput.close()
            return
        }
        try {
            ensureGl()
            val slotRef = AtomicReference<OutputSlot?>(null)
            val surface = surfaceOutput.getSurface(executor) {
                // Event.REQUEST_CLOSE: stop drawing to this output and close it.
                slotRef.get()?.let { teardownOutput(it) }
                surfaceOutput.close()
            }
            val eglSurface = egl!!.createWindowSurface(surface)
            val slot = OutputSlot(surfaceOutput, eglSurface, surfaceOutput.size)
            slotRef.set(slot)
            outputs.add(slot)
        } catch (t: Throwable) {
            surfaceOutput.close()
            reportError(t)
        }
    }

    // ---- teardown ----

    private fun teardownInput(slot: InputSlot) {
        if (slot.closed) return
        slot.closed = true
        inputs.remove(slot)
        slot.surfaceTexture.setOnFrameAvailableListener(null)
        try {
            egl?.let { core -> scratch?.let { core.makeCurrent(it) } }
            GlUtil.deleteTexture(slot.texId)
        } catch (_: Throwable) {
        }
        slot.surface.release()
        slot.surfaceTexture.release()
    }

    private fun teardownOutput(slot: OutputSlot) {
        if (!outputs.remove(slot)) return
        val core = egl ?: return
        try {
            scratch?.let { core.makeCurrent(it) }
        } catch (_: Throwable) {
        }
        core.destroySurface(slot.eglSurface)
    }

    private fun teardown() {
        for (o in outputs.toList()) {
            teardownOutput(o)
            o.output.close()
        }
        for (i in inputs.toList()) teardownInput(i)
        val core = egl ?: return
        try {
            scratch?.let { core.makeCurrent(it) }
            program?.release()
            passthrough?.release()
            quad?.release()
            GlUtil.deleteTexture(lutTex)
            GlUtil.deleteTexture(hlTex)
            if (hlFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(hlFbo), 0)
        } catch (_: Throwable) {
        }
        scratch?.let { core.destroySurface(it) }
        core.release()
        egl = null
        scratch = null
        program = null
        passthrough = null
        quad = null
        lutTex = 0
        hlTex = 0
        hlFbo = 0
    }

    private fun reportError(t: Throwable) {
        if (failed) return
        failed = true
        onError(t)
    }

    // ---- per-frame ----

    private fun onFrame(slot: InputSlot) {
        val core = egl
        if (core == null || slot.closed || released.get()) return
        try {
            core.makeCurrent(scratch!!)
            slot.surfaceTexture.updateTexImage()
            slot.surfaceTexture.getTransformMatrix(texMatrix)
            if (outputs.isEmpty() || failed) return

            val s = state.get()
            val lut = s.lut
            if (lutUploaded !== lut) {
                GlUtil.deleteTexture(lutTex)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                lutTex = LutTexture.upload(lut)
                lutUploaded = lut
            }
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, slot.texId)

            frame++
            val seed = s.params.seed + frame // grain animates in the preview
            val prog = program!!
            val rect = QUAD_FULL
            for (o in outputs.toList()) {
                o.output.updateTransformMatrix(o.matrix, texMatrix)
                core.makeCurrent(o.eglSurface)
                GLES30.glViewport(0, 0, o.size.width, o.size.height)
                prog.use()
                prog.setUniforms(s.params, o.size.width, o.size.height, seed, o.matrix, false, rect, lut.size)
                quad!!.draw()
                core.setPresentationTime(o.eglSurface, slot.surfaceTexture.timestamp)
                core.swap(o.eglSurface)
            }
            core.makeCurrent(scratch!!)

            if (frame % HIGHLIGHT_EVERY == 0) {
                val first = outputs.firstOrNull()
                if (first != null) sampleHighlight(first, slot.texId)
            }
        } catch (t: Throwable) {
            reportError(t)
        }
    }

    /** Renders the unfiltered frame into a tiny FBO and looks for the brightest region. */
    private fun sampleHighlight(out: OutputSlot, texId: Int) {
        val long = HL_LONG
        val short = max(8, (long * minOf(out.size.width, out.size.height).toFloat() /
            max(out.size.width, out.size.height)).roundToInt())
        val w = if (out.size.width >= out.size.height) long else short
        val h = if (out.size.width >= out.size.height) short else long
        if (hlFbo == 0 || w != hlW || h != hlH) {
            GlUtil.deleteTexture(hlTex)
            if (hlFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(hlFbo), 0)
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            hlTex = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hlTex)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glGenFramebuffers(1, ids, 0)
            hlFbo = ids[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, hlFbo)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, hlTex, 0,
            )
            hlW = w
            hlH = h
            hlBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, hlFbo)
        GLES30.glViewport(0, 0, w, h)
        passthrough!!.use(out.matrix, false, 1f / w, 1f / h)
        quad!!.draw()
        val buf = hlBuf!!
        buf.clear()
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GlUtil.checkError("highlight")
        _highlight.value = findHighlight(buf, w, h)
    }

    private companion object {
        const val HIGHLIGHT_EVERY = 10
        const val HL_LONG = 32
        val QUAD_FULL = floatArrayOf(0f, 0f, 1f, 1f)
    }
}

/**
 * Brightest region of an RGBA8 read-back (rows bottom-up, as glReadPixels returns them).
 * Returns UV of the upright frame (origin top-left) or null if nothing exceeds [threshold].
 */
internal fun findHighlight(buf: ByteBuffer, w: Int, h: Int, threshold: Float = 0.92f): Highlight? {
    fun luma(i: Int): Float {
        val o = i * 4
        val r = (buf.get(o).toInt() and 0xFF) / 255f
        val g = (buf.get(o + 1).toInt() and 0xFF) / 255f
        val b = (buf.get(o + 2).toInt() and 0xFF) / 255f
        return FilmMath.luma(r, g, b)
    }
    var best = -1f
    var bx = 0
    var by = 0
    for (y in 0 until h) for (x in 0 until w) {
        val l = luma(y * w + x)
        if (l > best) {
            best = l
            bx = x
            by = y
        }
    }
    if (best < threshold) return null
    var sx = 0f
    var sy = 0f
    var sw = 0f
    for (y in max(0, by - 5)..minOf(h - 1, by + 5)) for (x in max(0, bx - 5)..minOf(w - 1, bx + 5)) {
        val l = luma(y * w + x)
        if (l >= threshold) {
            sx += (x + 0.5f) * l
            sy += (y + 0.5f) * l
            sw += l
        }
    }
    val cx = sx / sw / w
    val cy = 1f - sy / sw / h
    return Highlight(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f), best)
}
