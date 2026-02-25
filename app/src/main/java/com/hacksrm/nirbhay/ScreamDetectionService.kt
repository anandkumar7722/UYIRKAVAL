package com.hacksrm.nirbhay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.hacksrm.nirbhay.sos.RiskScoreEngine
import com.hacksrm.nirbhay.sos.ScreamDetector
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ============================================================
 * ScreamDetectionService  –  Edge-AI Foreground Service
 * ============================================================
 *
 * Continuously records audio from the device microphone and
 * classifies it using the on-device **YAMNet** TFLite model.
 *
 * When a distress sound (Scream, Yell, Crying/sobbing) is
 * detected with confidence > CONFIDENCE_THRESHOLD, the service:
 *   1. Logs a red `Log.e` warning.
 *   2. Sends a local broadcast [ACTION_SOS_TRIGGERED] with
 *      extra "REASON" = "SCREAM_DETECTED".
 *   3. Applies a 5-second cooldown to avoid spam.
 *
 * Mic Sanity:
 *   • Before every inference cycle, the service reads a raw PCM
 *     buffer from the AudioRecord, computes its RMS volume, and
 *     logs it.  If RMS == 0.0 the mic is muted / permission
 *     is being denied silently by Android.
 *
 * Lifecycle:
 *   • Started via `startForegroundService(intent)`.
 *   • Runs as a FOREGROUND service (mic type) so Android
 *     won't kill it and API 34+ grants mic access.
 *   • Stopped via `stopService(intent)` or `stopSelf()`.
 *   • All resources (AudioRecord, classifier) are released
 *     in [onDestroy].
 *
 * Threading:
 *   • Inference runs on a dedicated daemon thread so the
 *     main thread is never blocked.
 *   • An [AtomicBoolean] flag (`isListening`) is used for
 *     thread-safe start / stop signalling.
 * ============================================================
 */
class ScreamDetectionService : Service() {

    // ── Constants ────────────────────────────────────────────
    companion object {
        private const val TAG = "ScreamDetection"

        /** Broadcast action fired when a scream/cry is detected. */
        const val ACTION_SOS_TRIGGERED = "ACTION_SOS_TRIGGERED"

        /** Extra key carrying the detection reason string. */
        const val EXTRA_REASON = "REASON"

        /** Foreground notification channel shared with FallDetectionService. */
        private const val CHANNEL_ID = "edge_ai_channel"
        private const val NOTIFICATION_ID = 1001

        /** TFLite model filename inside assets/. */
        private const val MODEL_FILE = "yamnet.tflite"

        /**
         * YAMNet labels that indicate distress.
         * These are the exact class names in the model's label map.
         * Extended set covers label variations across YAMNet versions.
         */
        private val DISTRESS_LABELS = setOf(
            "Scream",
            "Screaming",
            "Yell",
            "Shout",
            "Crying, sobbing",
            "Wail, moan",
            "Whimper",
            "Groan",
            "Battle cry",
            "Children shouting",
        )

        /**
         * Minimum confidence score (0 – 1) to consider a detection valid.
         * LOWERED from 0.60 → 0.30 for real-world mobile mic conditions.
         * Mobile mics distort frequencies and YAMNet struggles to reach
         * high confidence in noisy environments.
         */
        private const val CONFIDENCE_THRESHOLD = 0.30f

        /** Cooldown (ms) after a detection before the next one can fire. */
        private const val COOLDOWN_MS = 5_000L

        /**
         * Number of consecutive inference frames that must exceed the
         * threshold before we fire the SOS broadcast.  This prevents
         * a single noisy frame from triggering a false alarm.
         * 2 consecutive frames ≈ ~1.2 s of sustained distress sound.
         */
        private const val CONSECUTIVE_FRAMES_REQUIRED = 2

        /** Below this RMS the mic is effectively silent/muted. */
        private const val SILENT_RMS_THRESHOLD = 10f
    }

    // ── State ────────────────────────────────────────────────
    private var screamDetector: ScreamDetector? = null

    // ── Service lifecycle ────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created – setting up foreground notification")
        createNotificationChannel()
        // Use ServiceCompat to correctly pass foreground service type on Android 14+
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (screamDetector != null) {
            Log.w(TAG, "Already listening – ignoring duplicate start command")
            return START_STICKY
        }

        Log.i(TAG, "Starting scream detection (wrapper)")
        startListening()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed – releasing resources")
        stopListening()
        super.onDestroy()
    }

    // ── Notification helpers ─────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SHE-SHIELD Protection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps Edge-AI protection services running in the background."
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SHE-SHIELD Active")
            .setContentText("Listening for distress sounds…")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ── Audio recording & inference ──────────────────────────

    private fun startListening() {
        screamDetector = ScreamDetector(this.applicationContext) { label, score ->
            Log.w(TAG, "ScreamDetector callback: $label ${(score * 100).toInt()}%")
            RiskScoreEngine.addScore(50, "scream_detected", this)

            val sosIntent = Intent(ACTION_SOS_TRIGGERED).apply {
                putExtra(EXTRA_REASON, "SCREAM_DETECTED")
                putExtra("LABEL", label)
                putExtra("SCORE", score)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(sosIntent)
        }
        screamDetector?.start()
    }

    private fun stopListening() {
        screamDetector?.stop()
        screamDetector = null
    }
}
