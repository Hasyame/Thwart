package com.hasyame.marvelchampions.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.hasyame.marvelchampions.data.photos.PhotoStore
import java.io.File

/**
 * Opens an empty mail draft addressed to the project.
 *
 * Deliberately empty. Whatever somebody wants to say, they can say it; the app
 * filling the body in first means scrolling past it or deleting it before you
 * can start, and the device details it used to append are not worth that on a
 * message that is as likely to be a question as a bug.
 *
 * `ACTION_SENDTO` with a `mailto:` URI so only mail apps offer to handle it.
 * Returns false when none is installed, so the caller can say so instead of
 * appearing to do nothing.
 */
fun sendContactEmail(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$CONTACT_ADDRESS".toUri()
    }

    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Opens a mail draft with the last crash attached as a text file.
 *
 * An attachment rather than the body: a stack trace pasted into a message is
 * long enough to bury anything the sender wants to say alongside it, and mail
 * apps reflow it into something harder to read. The file carries the app
 * version and the device with the trace, because a trace without them is half
 * a report.
 *
 * Nothing is sent by the app. The draft opens, the person reads what is
 * attached, and they decide whether to send it. If the file cannot be written
 * the draft still opens with the trace in the body: worse, but not nothing,
 * and somebody reporting a crash should not meet a second one.
 */
fun sendCrashEmail(context: Context, trace: String): Boolean {
    val version = versionName(context)
    val report = crashReport(context, trace, version)

    val intent = if (report != null) {
        Intent(Intent.ACTION_SEND).apply {
            // A mail type rather than text/plain: this is going to a person,
            // not to whichever app last accepted a share.
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_ADDRESS))
            putExtra(Intent.EXTRA_STREAM, report)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$CONTACT_ADDRESS".toUri()
            putExtra(Intent.EXTRA_TEXT, trace)
        }
    }
    intent.putExtra(Intent.EXTRA_SUBJECT, "Marvel Champions Companion crash ($version)")

    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Writes the file the draft attaches, and hands back a URI for it.
 *
 * One file, overwritten each time, in a folder of its own. Its own folder
 * because the provider sharing it is scoped to exactly what may leave the
 * device, and the app's files directory also holds the campaign log, the
 * backup and the table photographs. None of those belong in a bug report.
 */
private fun crashReport(context: Context, trace: String, version: String): Uri? = runCatching {
    val directory = File(context.filesDir, CRASH_DIRECTORY).apply { mkdirs() }
    val file = File(directory, CRASH_FILE)
    file.writeText(
        buildString {
            appendLine("App version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(trace)
        },
    )
    FileProvider.getUriForFile(
        context,
        "${context.packageName}${PhotoStore.AUTHORITY_SUFFIX}",
        file,
    )
}.getOrNull()

private fun versionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "unknown"

private const val CRASH_DIRECTORY = "crash_reports"
private const val CRASH_FILE = "last-crash.txt"

const val CONTACT_ADDRESS: String = "marvelchampcompanion@proton.me"
