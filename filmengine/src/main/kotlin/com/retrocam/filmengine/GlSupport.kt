package com.retrocam.filmengine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal EGL 1.4 wrapper: one ES 3.x context, window and pbuffer surfaces. GL-thread only. */
internal class EglCore {
    val display: EGLDisplay
    val context: EGLContext
    private val config: EGLConfig

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
            "No EGL config for OpenGL ES 3"
        }
        config = configs[0]!!
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: 0x${Integer.toHexString(EGL14.eglGetError())}" }
    }

    fun createWindowSurface(surface: android.view.Surface): EGLSurface {
        val s = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(s != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}" }
        return s
    }

    fun createPbuffer(width: Int, height: Int): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val s = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        check(s != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}" }
        return s
    }

    fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
            "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
        }
    }

    fun swap(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun setPresentationTime(surface: EGLSurface, nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nanos)
    }

    fun destroySurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(display, surface)
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }
}

/** Shared GL helpers. GL-thread only. */
internal object GlUtil {
    fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log")
        }
        return shader
    }

    fun link(vertex: String, fragment: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw IllegalStateException("Program link failed: $log")
        }
        return program
    }

    fun checkError(where: String) {
        val e = GLES30.glGetError()
        if (e != GLES30.GL_NO_ERROR) throw IllegalStateException("GL error 0x${Integer.toHexString(e)} at $where")
    }

    val IDENTITY_MATRIX = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** Creates a texture for the camera's external image. */
    fun createExternalTexture(): Int {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    fun deleteTexture(id: Int) {
        if (id != 0) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
    }
}

/** Uploads a [Lut] as a GL_TEXTURE_3D (RGB8, linear filtering, clamp to edge). Returns the texture id. */
internal object LutTexture {
    fun upload(lut: Lut): Int {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        val buf = ByteBuffer.allocateDirect(lut.rgb.size).order(ByteOrder.nativeOrder())
        buf.put(lut.rgb).position(0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        // x = r (fastest), y = g, z = b: matches Lut.rgb index ((b*size + g)*size + r)*3.
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8, lut.size, lut.size, lut.size, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buf,
        )
        GlUtil.checkError("LUT upload")
        return t[0]
    }
}

/** Unit quad (0..1) as a VAO/VBO triangle strip. */
internal class FullscreenQuad {
    private val vao: Int
    private val vbo: Int

    init {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        val data = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().put(data)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
    }
}

/** The film program (shared by both paths) plus the single place where [FilmParams] become uniforms. */
internal class FilmProgram(external: Boolean) {
    val id: Int = GlUtil.link(FilmShader.VERTEX, FilmShader.fragmentSource(external))
    private val locations = HashMap<String, Int>()

    private fun loc(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    /** Binds the program and fixes the sampler units: scene = 0, LUT = 1, film-flash illumination map = 2. */
    fun use() {
        GLES30.glUseProgram(id)
        GLES30.glUniform1i(loc("uScene"), 0)
        GLES30.glUniform1i(loc("uLut"), 1)
        GLES30.glUniform1i(loc("uIllum"), 2)
    }

    /**
     * @param quad x0, y0, w, h of the drawn area inside the full output (GL-up, 0..1)
     * @param topDown true when output row 0 is the image top (capture); false for GL-up (preview)
     */
    fun setUniforms(
        p: FilmParams, outW: Int, outH: Int, seed: Int, texMatrix: FloatArray,
        topDown: Boolean, quad: FloatArray, lutSize: Int,
    ) {
        GLES30.glUniform4fv(loc("uQuadRect"), 1, quad, 0)
        GLES30.glUniform1f(loc("uTopDown"), if (topDown) 1f else 0f)
        GLES30.glUniformMatrix4fv(loc("uTexMat"), 1, false, texMatrix, 0)
        GLES30.glUniform1i(loc("uSeed"), seed)

        for ((name, v) in FilmUniforms.compute(p, outW, outH, lutSize)) {
            when (v.size) {
                1 -> GLES30.glUniform1f(loc(name), v[0])
                2 -> GLES30.glUniform2f(loc(name), v[0], v[1])
                3 -> GLES30.glUniform3f(loc(name), v[0], v[1], v[2])
                else -> GLES30.glUniform4f(loc(name), v[0], v[1], v[2], v[3])
            }
        }
    }

    fun release() {
        GLES30.glDeleteProgram(id)
    }
}

/** Passthrough program for the tiny highlight read-back. */
internal class PassthroughProgram(external: Boolean) {
    val id: Int = GlUtil.link(FilmShader.VERTEX, FilmShader.passthroughSource(external))
    private val locations = HashMap<String, Int>()
    private fun loc(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun use(texMatrix: FloatArray, topDown: Boolean, cellU: Float, cellV: Float) {
        GLES30.glUseProgram(id)
        GLES30.glUniform1i(loc("uScene"), 0)
        GLES30.glUniform4f(loc("uQuadRect"), 0f, 0f, 1f, 1f)
        GLES30.glUniform1f(loc("uTopDown"), if (topDown) 1f else 0f)
        GLES30.glUniformMatrix4fv(loc("uTexMat"), 1, false, texMatrix, 0)
        GLES30.glUniform2f(loc("uCell"), cellU, cellV)
    }

    fun release() {
        GLES30.glDeleteProgram(id)
    }
}
