package com.retrocam.logic

import kotlinx.coroutines.delay

/** Self-timer: reports N, N-1, ..., 1 once per second, then returns. Cancel by cancelling the coroutine. */
object Countdown {
    suspend fun run(seconds: Int, onTick: (Int) -> Unit) {
        for (remaining in seconds downTo 1) {
            onTick(remaining)
            delay(1000L)
        }
    }
}
