package com.retrocam.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.retrocam.CrashLogger
import com.retrocam.R

/** Opens the photo in the system gallery. Returns false when nothing can handle it. */
fun openPhoto(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .also { it.clipData = ClipData.newRawUri("", uri) }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Shares filesDir/crash/last_crash.txt through FileProvider. Returns a string res on failure, null on success. */
fun shareCrashLog(context: Context, crashLogger: CrashLogger): Int? {
    val file = crashLogger.lastCrashFile() ?: return R.string.msg_no_crash_log
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_share_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri("", uri) }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.crash_share_title)))
        null
    } catch (_: Throwable) {
        R.string.msg_share_failed
    }
}

fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
    }
}
