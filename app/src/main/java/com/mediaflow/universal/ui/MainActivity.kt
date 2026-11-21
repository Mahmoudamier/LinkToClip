package com.mediaflow.universal.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.mediaflow.universal.core.CrashReporter
import com.mediaflow.universal.data.UserPreferences
import com.mediaflow.universal.ui.theme.MediaFlowTheme
import timber.log.Timber
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            val arabic = Locale("ar")
            setLocale(arabic)
            setLayoutDirection(arabic)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notifications are optional; downloads work without them. */ }

    private var permissionsRequested = false

    override fun onStart() {
        super.onStart()
        if (permissionsRequested) return
        permissionsRequested = true
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 42)
            }
        }.onFailure { Timber.w(it, "Permission request skipped") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedLink = intent.getStringExtra(Intent.EXTRA_TEXT).takeIf { it?.contains("http") == true }

        setContent {
            val context = LocalContext.current
            val prefs = remember { UserPreferences(applicationContext) }
            val prefsState by prefs.flow.collectAsState(initial = com.mediaflow.universal.data.UserPrefs())
            var crashText by remember { mutableStateOf(CrashReporter.read(context)) }
            MediaFlowTheme(themeMode = prefsState.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(sharedLink = sharedLink)
                }
                if (crashText != null) {
                    AlertDialog(
                        onDismissRequest = {
                            CrashReporter.clear(context)
                            crashText = null
                        },
                        title = { Text("تم رصد خطأ") },
                        text = { Text(stringResource(com.mediaflow.universal.R.string.crash_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, crashText)
                                }
                                runCatching {
                                    startActivity(
                                        Intent.createChooser(
                                            intent,
                                            context.getString(com.mediaflow.universal.R.string.send_error)
                                        )
                                    )
                                }
                                CrashReporter.clear(context)
                                crashText = null
                            }) { Text(stringResource(com.mediaflow.universal.R.string.send_error)) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                CrashReporter.clear(context)
                                crashText = null
                            }) { Text(stringResource(com.mediaflow.universal.R.string.close)) }
                        }
                    )
                }
            }
        }
    }
}
