package com.personal.myfiles.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.personal.myfiles.MyFilesApp
import com.personal.myfiles.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OperationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "عمليات الملفات", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun build(title: String, text: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, build("جارٍ التنفيذ…", "", 0, true),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            val ops = (application as MyFilesApp).container.ops
            val nm = getSystemService(NotificationManager::class.java)
            job = scope.launch {
                ops.ops.collect { list ->
                    val active = list.filter { !it.finished }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@OperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val first = active.first()
                        val more = if (active.size > 1) " (+${active.size - 1})" else ""
                        nm.notify(NOTIF_ID, build(first.title + more, first.current, (first.fraction * 100).toInt(), first.total <= 0))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "ops"
        const val NOTIF_ID = 77
    }
}
