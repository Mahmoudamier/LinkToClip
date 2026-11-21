package com.mediaflow.universal.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes uncaught exceptions to a file so they can be shared from the device. */
object CrashReporter {
    private const val FILE_NAME = "crash-report.txt"

    fun install(context: Context) {
        val folder = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                File(folder, FILE_NAME).writeText(
                    "Time: $time\nThread: ${thread.name}\n\n$sw"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
