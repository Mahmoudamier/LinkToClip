package com.mediaflow.universal

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import com.mediaflow.universal.core.CrashReporter
import com.mediaflow.universal.download.DownloadNotifications
import com.mediaflow.universal.extraction.engines.YtDlpEngine
import timber.log.Timber

class MediaFlowApp : Application() {

    /** The app is Arabic-only, independent of the device language. */
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration).apply {
            val arabic = Locale("ar")
            setLocale(arabic)
            setLayoutDirection(arabic)
        }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        CrashReporter.install(this)
        runCatching { DownloadNotifications.createChannel(this) }
            .onFailure { Timber.e(it, "Notification channel creation failed") }
        // Unpacking the on-device extraction runtime happens once in the background.
        // Any failure here must never prevent the UI from opening.
        runCatching { YtDlpEngine.initialize(this) }
            .onFailure { Timber.e(it, "yt-dlp initialisation failed") }
    }
}
