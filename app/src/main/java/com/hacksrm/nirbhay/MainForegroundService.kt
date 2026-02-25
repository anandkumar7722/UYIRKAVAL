package com.hacksrm.nirbhay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.hacksrm.nirbhay.connectivity.ConnectivityHelper
import com.hacksrm.nirbhay.sos.UploadQueueWorker
import java.util.concurrent.TimeUnit

class MainForegroundService : Service() {
    companion object {
        private const val TAG = "MainForegroundService"
        private const val CHANNEL_ID = "nirbhay_main"
        private const val NOTIFICATION_ID = 9001
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Start detectors (they are separate foreground services)
        try {
            startForegroundService(Intent(this, ScreamDetectionService::class.java))
            startForegroundService(Intent(this, FallDetectionService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start edge-AI services: ${t.message}")
        }

        // Start Bridgefy mesh
        try {
            // NirbhayApp already calls BridgefyMesh.init in Application.onCreate
            // MainActivity.startMesh() calls BridgefyMesh.start() after permissions are granted.
            // Do NOT call BridgefyMesh.start() here — it would double-start Bridgefy.
            Log.d(TAG, "Bridgefy start is handled by MainActivity after permissions")
        } catch (t: Throwable) {
            Log.w(TAG, "BridgefyMesh block: ${t.message}")
        }

        // Register network callback to enqueue immediate upload when network returns
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "Network available — triggering immediate upload")
                    val oneTime = OneTimeWorkRequestBuilder<UploadQueueWorker>().build()
                    WorkManager.getInstance(applicationContext).enqueue(oneTime)
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register network callback: ${t.message}")
        }

        // Schedule periodic upload worker every 15 minutes
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodic = PeriodicWorkRequestBuilder<UploadQueueWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "sos_upload_queue",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to schedule periodic worker: ${t.message}")
        }

        Log.d(TAG, "MainForegroundService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to unregister network callback: ${t.message}")
        }

        try {
            BridgefyMesh.sendSos(SOSPacket("shutdown", 0.0, 0.0, System.currentTimeMillis(), "shutdown", 0))
        } catch (t: Throwable) {
            // ignore
        }

        Log.d(TAG, "MainForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nirbhay Main",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "SHE-SHIELD core foreground service" }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SHE-SHIELD Active")
            .setContentText("Monitoring for your safety…")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}

