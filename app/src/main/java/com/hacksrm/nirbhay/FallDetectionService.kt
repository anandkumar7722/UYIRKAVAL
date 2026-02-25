package com.hacksrm.nirbhay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hacksrm.nirbhay.sos.RiskScoreEngine
import kotlin.math.sqrt

/**
 * ============================================================
 * FallDetectionService  –  Kinetic Edge-AI Foreground Service
 * ============================================================
 *
 * Continuously monitors the device accelerometer to detect a
 * sudden fall or violent struggle using a **two-phase state
 * machine**:
 *
 *   Phase 1 – FREE-FALL:  gForce < 0.3 G
 *       The phone is in free-fall (weightless).  When detected,
 *       a 1-second window opens to look for impact.
 *
 *   Phase 2 – IMPACT:  gForce > 2.5 G within 1 000 ms of
 *       the free-fall event.  A sudden deceleration (hitting
 *       the ground / wall) following free-fall is the classic
 *       kinematic signature of a fall.
 *
 * If both phases are satisfied → the service:
 *   1. Logs a 🚨 `Log.e` warning (visible in Logcat).
 *   2. Sends a [LocalBroadcast] on [ACTION_SOS_TRIGGERED]
 *      with extra `REASON = "FALL_DETECTED"`.
 *   3. Enters a 10-second cooldown to prevent duplicate
 *      triggers while the phone bounces.
 *
 * Physics Primer (for reviewers / maintainers):
 * ─────────────────────────────────────────────
 * The accelerometer returns values in m/s².  When the phone is
 * stationary on a table, the sensor reads ≈ (0, 0, 9.81) due
 * to gravity.  We normalise to "G-force":
 *
 *   gForce = √(x² + y² + z²) / 9.80665
 *
 * • gForce ≈ 1.0  →  at rest (gravity only)
 * • gForce ≈ 0.0  →  free-fall (no net acceleration)
 * • gForce > 2.0  →  sudden impact / jerk
 *
 * Lifecycle:
 *   • Started via `startForegroundService(intent)`.
 *   • Binds to the **same** notification channel as
 *     [ScreamDetectionService] (`edge_ai_channel`) so the
 *     user sees only ONE persistent notification.
 *   • Stopped via `stopService(intent)` or `stopSelf()`.
 *   • Sensor listener is unregistered in [onDestroy].
 * ============================================================
 */
class FallDetectionService : Service() {

    // ── Constants ────────────────────────────────────────────
    companion object {
        private const val TAG = "FallDetection"

        /**
         * Shared broadcast action – identical to the one used by
         * [ScreamDetectionService] so that a single receiver in
         * the Activity / ViewModel can handle both SOS sources.
         */
        const val ACTION_SOS_TRIGGERED = "ACTION_SOS_TRIGGERED"
        const val EXTRA_REASON = "REASON"

        /**
         * Notification channel shared with [ScreamDetectionService].
         * Re-registering the same channel ID is a safe no-op; only
         * the first registration defines the importance level.
         */
        private const val CHANNEL_ID = "edge_ai_channel"

        /**
         * Unique notification ID – MUST differ from
         * [ScreamDetectionService.NOTIFICATION_ID] (1001) because
         * two foreground services in the same process each need
         * their own notification.
         */
        private const val NOTIFICATION_ID = 1002

        // ── Physics thresholds ───────────────────────────────

        /** Standard gravity in m/s² (ISO 80000-3). */
        private const val STANDARD_GRAVITY = 9.80665f

        /**
         * Free-fall threshold in G.
         * During a genuine free-fall the phone experiences near-zero
         * net acceleration (all axes cancel out), yielding gForce ≈ 0.
         * We use a generous 0.3 G to account for air resistance and
         * slight rotational forces.
         */
        private const val FREE_FALL_THRESHOLD_G = 0.3f

        /**
         * Impact threshold in G.
         * A phone striking a hard surface easily exceeds 3–8 G.
         * 2.5 G is conservative enough to catch falls onto a bed
         * or carpet while filtering normal pocket movements.
         */
        private const val IMPACT_THRESHOLD_G = 2.5f

        /**
         * Maximum milliseconds between the free-fall event and the
         * subsequent impact for the pair to count as a "fall".
         * 1 000 ms covers a ~5 m drop height (physics: t = √(2h/g)).
         */
        private const val IMPACT_WINDOW_MS = 1_000L

        /**
         * Cooldown (ms) after a confirmed fall before the next
         * detection can fire.  Prevents dozens of triggers while
         * the phone bounces.
         */
        private const val COOLDOWN_MS = 10_000L
    }

    // ── Mutable state (all accessed from the sensor callback
    //    thread, which is the main/UI thread by default) ──────

    private var fallDetector: com.hacksrm.nirbhay.sos.FallDetector? = null

    // ── Service lifecycle ────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created – initialising foreground notification")
        createNotificationChannel()
        // Use ServiceCompat to correctly pass foreground service type on Android 14+
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else 0
        )
        // Create and start FallDetector helper
        fallDetector = com.hacksrm.nirbhay.sos.FallDetector(this.applicationContext) {
            // onFallDetected callback
            Log.d(TAG, "FallDetector callback invoked — adding score and broadcasting")
            RiskScoreEngine.addScore(40, "fall_detected", this)

            val sosIntent = Intent(ACTION_SOS_TRIGGERED).apply {
                putExtra(EXTRA_REASON, "FALL_DETECTED")
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(sosIntent)
        }
        fallDetector?.start()
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed – unregistering sensor listener")
        fallDetector?.stop()
        fallDetector = null
        super.onDestroy()
    }

    // ── Notification (shared channel with ScreamDetectionService) ─

    /**
     * Create (or re-register) the low-importance notification
     * channel used by all Edge-AI foreground services.
     */
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
            .setContentText("Monitoring for falls & impacts…")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
