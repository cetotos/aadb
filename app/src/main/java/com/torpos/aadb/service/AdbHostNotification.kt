package com.torpos.aadb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.torpos.aadb.MainActivity
import com.torpos.aadb.R
import com.torpos.aadb.state.AdbHostRepository
import com.torpos.aadb.state.ConnectionState

object AdbHostNotification {
    const val CHANNEL_ID = "adb_host"
    const val NOTIFICATION_ID = 1001

    const val ACTION_PAIR_INPUT = "com.torpos.aadb.action.PAIR_INPUT"
    const val KEY_PAIR_INPUT = "pair_input"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADB Host",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ADB host pairing and status"
        }
        manager.createNotificationChannel(channel)
    }

    fun post(context: Context) {
        ensureChannel(context)
        val notification = buildNotification(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun buildNotification(context: Context): Notification {
        val state = AdbHostRepository.state.value
        val status = when (state.connectionState) {
            ConnectionState.Idle -> "Idle"
            ConnectionState.Pairing -> "Pairing"
            ConnectionState.Paired -> "Paired"
            ConnectionState.Connected -> "Connected"
            ConnectionState.Error -> "Error"
        }

        val contentText = if (state.serverRunning) {
            "Server running · $status"
        } else {
            "Server stopped · $status"
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pairIntent = Intent(context, AdbHostNotificationReceiver::class.java)
            .setAction(ACTION_PAIR_INPUT)
        val pairPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            pairIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_PAIR_INPUT)
            .setLabel("Pairing code")
            .build()

        val pairAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "ENTER CODE",
            pairPendingIntent
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ADB Host")
            .setContentText(contentText)
            .setSubText("Enter the 6-digit pairing code")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pairAction)
            .build()
    }
}
