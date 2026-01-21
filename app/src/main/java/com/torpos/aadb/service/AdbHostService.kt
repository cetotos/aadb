package com.torpos.aadb.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

class AdbHostService : Service() {
    override fun onCreate() {
        super.onCreate()
        com.torpos.aadb.state.AdbHostRepository.initialize(applicationContext)
        AdbHostNotification.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                AdbHostNotification.NOTIFICATION_ID,
                AdbHostNotification.buildNotification(this),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AdbHostNotification.NOTIFICATION_ID, AdbHostNotification.buildNotification(this))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AdbHostNotification.post(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AdbHostService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
