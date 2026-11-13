package com.retrocam

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLoggerTest {
    @Test
    fun reportContainsTraceAndDeviceInfo() {
        val text = CrashLogger.format("2026-11-13 10:00:00 +0000", "0.1.0 (1)", "Acme Phone", "main", IllegalStateException("boom"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("Device: Acme Phone"))
        assertTrue(text.contains("App: 0.1.0 (1)"))
        assertTrue(text.contains("Thread: main"))
        assertTrue(text.contains("CrashLoggerTest"))
    }
}
