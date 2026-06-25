package com.jd.softphone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.jd.softphone.sip.SipService
import com.jd.softphone.ui.SoftphoneRoot
import com.jd.softphone.ui.theme.SoftphoneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoftphoneTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    // The SIP foreground service uses the `microphone` FGS type, which on
                    // Android 14+ requires RECORD_AUDIO to be granted before it can start.
                    if (result[Manifest.permission.RECORD_AUDIO] == true) {
                        SipService.start(this@MainActivity)
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasRecordAudio()) {
                        SipService.start(this@MainActivity)
                    } else {
                        permissionLauncher.launch(requiredPermissions())
                    }
                }

                SoftphoneRoot()
            }
        }
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()
}
