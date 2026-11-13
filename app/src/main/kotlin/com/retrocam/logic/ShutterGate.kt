package com.retrocam.logic

/**
 * Decides whether a shutter press may start a capture. Single-threaded (call from the main thread).
 *
 * - Only one frame can be "in the sensor" at a time.
 * - Presses closer than [debounceMs] to the previous accepted one are ignored (bouncing volume keys).
 * - Finished frames are processed (GL, frame, save) in the background; at most [maxProcessing]
 *   run at once so memory stays bounded, but the next shutter press is NOT blocked by a save
 *   as long as the limit is not reached.
 */
class ShutterGate(
    private val maxProcessing: Int = 2,
    private val debounceMs: Long = 250L,
) {
    enum class Decision { ACCEPT, BUSY_CAPTURING, TOO_SOON, QUEUE_FULL }

    private var capturing = false
    private var lastAcceptedAt = Long.MIN_VALUE
    var processing = 0
        private set

    fun tryBeginCapture(nowMs: Long): Decision {
        if (capturing) return Decision.BUSY_CAPTURING
        if (lastAcceptedAt != Long.MIN_VALUE && nowMs - lastAcceptedAt < debounceMs) return Decision.TOO_SOON
        if (processing >= maxProcessing) return Decision.QUEUE_FULL
        capturing = true
        lastAcceptedAt = nowMs
        return Decision.ACCEPT
    }

    /** The sensor frame is in hand; hand it to the background pipeline. */
    fun captureFinished() {
        capturing = false
        processing++
    }

    fun captureFailed() {
        capturing = false
    }

    fun processingFinished() {
        if (processing > 0) processing--
    }
}
