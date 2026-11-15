package com.retrocam.filmengine

import android.graphics.Bitmap
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Capture path: the SAME shader on an EGL pbuffer on its own GL thread. Renders in
 * [TILE]x[TILE] viewport tiles (every tile samples the whole input texture, so neighbourhood
 * effects are seamless). Output has the input's dimensions; if the input exceeds
 * GL_MAX_TEXTURE_SIZE it is downscaled to fit as a *texture* only, the output is still rendered
 * at the full input size.
 *
 * Thread-safe and reusable: renders are serialised on the renderer's own GL thread.
 * Call [release] when done; further [render] calls then fail with [IllegalStateException].
 */
class OffscreenFilmRenderer {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "RetroCam-GL-offscreen") }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val released = AtomicBoolean(false)

    // GL thread only
    private var egl: EglCore? = null
    private var surface: EGLSurface? = null
    private var program: FilmProgram? = null
    private var quad: FullscreenQuad? = null
    private var fbo = 0
    private var fboTex = 0
    private var maxTextureSize = 0
    private var lutTex = 0
    private var lutUploaded: Lut? = null

    /**
     * [illum] is the film-flash ambient map (see [FlashAnalyzer]); only used when `params.relight != null`. When missing it is
     * computed from [input] on the GL thread, and when that fails the film flash is skipped.
     */
    suspend fun render(input: Bitmap, params: FilmParams, lut: Lut, illum: IllumMap? = null): Bitmap {
        check(!released.get()) { "OffscreenFilmRenderer is released" }
        return try {
            withContext(dispatcher) {
                check(!released.get()) { "OffscreenFilmRenderer is released" }
                renderOnGlThread(input, params, lut, illum)
            }
        } catch (e: RejectedExecutionException) {
            throw IllegalStateException("OffscreenFilmRenderer is released", e)
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    teardown()
                } catch (_: Throwable) {
                }
            }
        } catch (_: RejectedExecutionException) {
        }
        executor.shutdown()
    }

    // ---- GL thread ----

    private fun ensureGl() {
        if (egl != null) return
        val core = EglCore()
        try {
            val pb = core.createPbuffer(1, 1)
            core.makeCurrent(pb)
            egl = core
            surface = pb
            val max = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, max, 0)
            maxTextureSize = max[0]
            program = FilmProgram(external = false)
            quad = FullscreenQuad()
            val ids = IntArray(1)
            GLES30.glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            GLES30.glGenTextures(1, ids, 0)
            fboTex = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTex)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, TILE, TILE, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTex, 0,
            )
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                "Offscreen framebuffer incomplete"
            }
            GlUtil.checkError("offscreen init")
        } catch (t: Throwable) {
            teardown()
            if (egl == null) core.release()
            throw t
        }
    }

    private fun renderOnGlThread(input: Bitmap, params0: FilmParams, lut: Lut, illum0: IllumMap?): Bitmap {
        ensureGl()
        var params = params0
        var illum = illum0
        if (params.relight != null && illum == null) {
            illum = FlashAnalyzer.illumination(input)
        }
        if (illum == null) params = params.copy(relight = null) // no ambient map: skip the film flash rather than guess
        val egl = egl!!
        egl.makeCurrent(surface!!)
        val w = input.width
        val h = input.height

        // Software ARGB_8888 copy if needed (hardware / float bitmaps cannot be uploaded directly).
        var source = input
        var ownedSource: Bitmap? = null
        if (input.config != Bitmap.Config.ARGB_8888) {
            source = input.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Cannot copy input bitmap to ARGB_8888")
            ownedSource = source
        }
        // Texture-size limit: downscale the *texture*, still render at the full output size.
        var texBitmap = source
        var ownedTex: Bitmap? = null
        if (w > maxTextureSize || h > maxTextureSize) {
            val k = minOf(maxTextureSize.toFloat() / w, maxTextureSize.toFloat() / h)
            texBitmap = Bitmap.createScaledBitmap(
                source, maxOf(1, (w * k).toInt()), maxOf(1, (h * k).toInt()), true,
            )
            ownedTex = texBitmap
        }

        val sceneTex = IntArray(1)
        try {
            GLES30.glGenTextures(1, sceneTex, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTex[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, texBitmap, 0)
            GlUtil.checkError("input upload")
        } finally {
            ownedTex?.recycle()
            ownedSource?.recycle()
        }

        var illumTex = 0
        try {
            if (params.relight != null && illum != null) {
                val ids = IntArray(1)
                GLES30.glGenTextures(1, ids, 0)
                illumTex = ids[0]
                GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, illumTex)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
                val bytes = illum.toGammaBytes()
                val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                buf.put(bytes).rewind()
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB8, illum.width, illum.height, 0,
                    GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buf,
                )
                GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GlUtil.checkError("illum upload")
            }
            if (lutUploaded !== lut) {
                GlUtil.deleteTexture(lutTex)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                lutTex = LutTexture.upload(lut)
                lutUploaded = lut
            }
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

            val prog = program!!
            prog.use()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val readBuf = ByteBuffer.allocateDirect(TILE * TILE * 4).order(ByteOrder.nativeOrder())
            val pixels = IntArray(TILE * TILE)
            val rect = FloatArray(4)
            var y0 = 0
            while (y0 < h) {
                val th = minOf(TILE, h - y0)
                var x0 = 0
                while (x0 < w) {
                    val tw = minOf(TILE, w - x0)
                    rect[0] = x0.toFloat() / w
                    rect[1] = y0.toFloat() / h
                    rect[2] = tw.toFloat() / w
                    rect[3] = th.toFloat() / h
                    // Capture: output row 0 is the image top (topDown), so readPixels rows map 1:1
                    // onto bitmap rows - no vertical flip needed.
                    prog.setUniforms(params, w, h, params.seed, GlUtil.IDENTITY_MATRIX, true, rect, lut.size)
                    GLES30.glViewport(0, 0, tw, th)
                    quad!!.draw()
                    readBuf.clear()
                    GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readBuf)
                    GlUtil.checkError("tile read")
                    readBuf.rewind()
                    val ints = readBuf.asIntBuffer()
                    // Little-endian int from bytes R,G,B,A = 0xAABBGGRR -> ARGB.
                    val n = tw * th
                    for (i in 0 until n) {
                        val v = ints.get(i)
                        pixels[i] = (v and 0xFF00FF00.toInt()) or ((v and 0xFF) shl 16) or ((v shr 16) and 0xFF)
                    }
                    out.setPixels(pixels, 0, tw, x0, y0, tw, th)
                    x0 += tw
                }
                y0 += th
            }
            return out
        } finally {
            GlUtil.deleteTexture(sceneTex[0])
            if (illumTex != 0) GlUtil.deleteTexture(illumTex)
        }
    }

    private fun teardown() {
        val core = egl ?: return
        try {
            surface?.let { core.makeCurrent(it) }
            program?.release()
            quad?.release()
            GlUtil.deleteTexture(lutTex)
            GlUtil.deleteTexture(fboTex)
            if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        } catch (_: Throwable) {
        }
        surface?.let { core.destroySurface(it) }
        core.release()
        egl = null
        surface = null
        program = null
        quad = null
        lutTex = 0
        fboTex = 0
        fbo = 0
        lutUploaded = null
    }

    private companion object {
        const val TILE = 1024
    }
}
