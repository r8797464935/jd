package com.jd.softphone.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.jd.softphone.MainActivity
import com.jd.softphone.R
import com.jd.softphone.data.SipAccountStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the Linphone Core alive (and therefore the SIP
 * registration) while the app is backgrounded. The persistent notification reflects
 * the current registration status.
 */
@AndroidEntryPoint
class SipService : LifecycleService() {

    @Inject lateinit var linphoneManager: LinphoneManager
    @Inject lateinit var accountStore: SipAccountStore

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIF_ID, buildNotification(RegistrationStatus.NONE))

        linphoneManager.start()
        accountStore.account.value?.let { linphoneManager.configureAccount(it) }

        // Keep the notification in sync with registration + (re)apply account changes.
        lifecycleScope.launch {
            combine(accountStore.account, linphoneManager.registration) { account, status ->
                account to status
            }.collect { (_, status) ->
                notificationManager().notify(NOTIF_ID, buildNotification(status))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun buildNotification(status: RegistrationStatus): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (status) {
            RegistrationStatus.REGISTERED -> getString(R.string.service_registered)
            else -> getString(R.string.service_unregistered)
        }
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannels() {
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_service_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALLS,
                getString(R.string.channel_calls_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.channel_calls_desc) },
        )
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_SERVICE = "sip_service"
        const val CHANNEL_CALLS = "sip_calls"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, SipService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
