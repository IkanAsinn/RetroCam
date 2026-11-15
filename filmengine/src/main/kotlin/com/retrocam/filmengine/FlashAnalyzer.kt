package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.media.FaceDetector
import kotlin.math.max

/**
 * Android side of the film flash: downscales the captured frame once, estimates the ambient illumination map
 * ([IllumMapper]) and looks for faces with the platform [FaceDetector] to place the flash light. Every failure degrades
 * gracefully: no faces -> [FlashSubject.DEFAULT]; no analysis at all -> null (the caller skips the film flash).
 */
object FlashAnalyzer {
    class Result(val subject: FlashSubject, val illum: IllumMap, val faces: List<FaceBox>) {
        val ambientLuma: Float get() = illum.meanLuma
    }

    private const val ANALYSIS_LONG_SIDE = 320
    private const val MAX_FACES = 4
    private const val MIN_CONFIDENCE = 0.4f
    private const val MIN_FACE_SIZE = 0.04f

    fun analyze(bitmap: Bitmap): Result? {
        val small = downscale(bitmap) ?: return null
        return try {
            val w = small.width
            val h = small.height
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            val illum = IllumMapper.fromArgb(px, w, h)
            val faces = detectFaces(small)
            Result(FlashSubject.fromFaces(faces), illum, faces)
        } catch (t: Throwable) {
            null
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    /** Only the ambient map (no face search). Null on failure. */
    fun illumination(bitmap: Bitmap): IllumMap? {
        val small = downscale(bitmap) ?: return null
        return try {
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            IllumMapper.fromArgb(px, small.width, small.height)
        } catch (t: Throwable) {
            null
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    /** Software ARGB_8888 copy of at most [ANALYSIS_LONG_SIDE] px on the long edge (halving steps keep it cheap and alias-free). */
    private fun downscale(src: Bitmap): Bitmap? = try {
        var cur: Bitmap = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        while (max(cur.width, cur.height) >= ANALYSIS_LONG_SIDE * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val k = ANALYSIS_LONG_SIDE.toFloat() / max(cur.width, cur.height)
        if (k < 1f) {
            val next = Bitmap.createScaledBitmap(cur, max(2, (cur.width * k).toInt()), max(2, (cur.height * k).toInt()), true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        cur
    } catch (t: Throwable) {
        null
    }

    private fun detectFaces(small: Bitmap): List<FaceBox> = try {
        val w = small.width and 1.inv() // FaceDetector needs an even width
        val h = small.height
        val rgb565 = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        try {
            Canvas(rgb565).drawBitmap(small, 0f, 0f, null)
            val found = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val n = FaceDetector(w, h, MAX_FACES).findFaces(rgb565, found)
            val out = ArrayList<FaceBox>()
            val mid = PointF()
            for (i in 0 until n) {
                val f = found[i] ?: continue
                if (f.confidence() < MIN_CONFIDENCE) continue
                f.getMidPoint(mid)
                val eyes = f.eyesDistance()
                val box = FaceBox(mid.x / w, (mid.y + 0.45f * eyes) / h, 2.6f * eyes / h)
                if (box.size >= MIN_FACE_SIZE) out += box
            }
            out
        } finally {
            rgb565.recycle()
        }
    } catch (t: Throwable) {
        emptyList()
    }
}
