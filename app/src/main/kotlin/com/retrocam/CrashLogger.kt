package com.retrocam

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last uncaught exception to filesDir/crash/last_crash.txt (stack trace plus
 * device and app info) and then hands over to the previous handler. The file stays on the
 * device until the user chooses "Send crash log" in settings. No network involved.
 */
class CrashLogger(private val context: Context) {
    private val file: File get() = File(File(context.filesDir, DIR), FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(thread, throwable)
            } catch (_: Throwable) {
                // Never let logging mask the original crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The last crash report, or null if the app has not crashed since install. */
    fun lastCrashFile(): File? = file.takeIf { it.isFile && it.length() > 0 }

    private fun write(thread: Thread, throwable: Throwable) {
        val target = file
        target.parentFile?.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        target.writeText(format(stamp, appVersion(), deviceInfo(), thread.name, throwable))
    }

    private fun appVersion(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (_: Throwable) {
        "unknown"
    }

    private fun deviceInfo(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}, ABI ${Build.SUPPORTED_ABIS.firstOrNull()}"

    companion object {
        const val DIR = "crash"
        const val FILE_NAME = "last_crash.txt"

        /** Pure formatter, unit-tested. */
        fun format(timestamp: String, appVersion: String, device: String, threadName: String, throwable: Throwable): String {
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            return buildString {
                appendLine("RetroCam crash report")
                appendLine("Time: $timestamp")
                appendLine("App: $appVersion")
                appendLine("Device: $device")
                appendLine("Thread: $threadName")
                appendLine()
                append(trace)
            }
        }
    }
}
