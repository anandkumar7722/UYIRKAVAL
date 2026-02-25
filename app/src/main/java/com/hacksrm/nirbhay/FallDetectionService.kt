package com.hacksrm.nirbhay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
class FallDetectionService : Service(), SensorEventListener {

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

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    /**
     * State-machine flag: `true` once free-fall is detected.
     * Reset after the impact window expires or a fall is confirmed.
     */
    private var freeFallDetected = false

    /** System clock timestamp (ms) when free-fall was first sensed. */
    private var freeFallTimestamp = 0L

    /**
     * System clock timestamp (ms) of the last confirmed fall.
     * Used for the 10-second cooldown guard.
     */
    private var lastTriggerTimestamp = 0L

    // ── Service lifecycle ────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created – initialising foreground notification")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerAccelerometer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand – sensor already registered in onCreate")
        return START_STICKY
    }

    /**
     * Tear-down: unregister the sensor listener so we stop
     * receiving callbacks and release the hardware lock.
     */
    override fun onDestroy() {
        Log.i(TAG, "Service destroyed – unregistering sensor listener")
        sensorManager?.unregisterListener(this)
        sensorManager = null
        accelerometer = null
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

    // ── Accelerometer registration ───────────────────────────

    /**
     * Acquire the system [SensorManager], find the accelerometer,
     * and register `this` as the listener.
     *
     * Sampling rate: [SensorManager.SENSOR_DELAY_GAME] (~20 ms / 50 Hz).
     * This is fast enough to detect a sub-second free-fall event
     * but gentle enough on battery compared to SENSOR_DELAY_FASTEST.
     */
    private fun registerAccelerometer() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer sensor available on this device!")
            stopSelf()
            return
        }

        sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME,   // ~50 Hz
        )
        Log.i(TAG, "Accelerometer listener registered at SENSOR_DELAY_GAME")
    }

    // ── SensorEventListener callbacks ────────────────────────

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op – accuracy changes don't affect our threshold logic.
    }

    /**
     * Called ~50 times / second (SENSOR_DELAY_GAME).
     *
     * **Algorithm – Two-Phase Fall Detection State Machine**
     *
     * ```
     *  ┌──────────┐   gForce < 0.3 G   ┌───────────────┐
     *  │  IDLE    │ ──────────────────► │  FREE-FALL    │
     *  └──────────┘                     └───────┬───────┘
     *        ▲                                  │
     *        │  window expired (>1 s)           │ gForce > 2.5 G
     *        │  or cooldown active              │ within 1 000 ms
     *        │                                  ▼
     *        │                          ┌───────────────┐
     *        └────────── cooldown ◄──── │  FALL         │
     *                    10 000 ms      │  CONFIRMED    │
     *                                   └───────────────┘
     * ```
     *
     * Step-by-step:
     *
     * 1. **Compute G-force magnitude**
     *    The accelerometer reports 3-axis acceleration in m/s².
     *    We combine them into a single scalar:
     *
     *        magnitude = √(ax² + ay² + az²)
     *        gForce    = magnitude / 9.80665
     *
     *    At rest gForce ≈ 1.0 (gravity).  In free-fall ≈ 0.0.
     *
     * 2. **Phase 1 – Detect free-fall** (gForce < 0.3)
     *    Record the timestamp.  Set the `freeFallDetected` flag.
     *
     * 3. **Phase 2 – Detect impact** (gForce > 2.5)
     *    Only valid if Phase 1 fired within the last 1 000 ms.
     *    If yes → FALL CONFIRMED.
     *
     * 4. **Cooldown** – ignore everything for 10 s after a
     *    confirmed fall so the phone bouncing doesn't re-trigger.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()

        // ── Cooldown guard ───────────────────────────────────
        if (now - lastTriggerTimestamp < COOLDOWN_MS) return

        // ── Step 1: Compute G-force ─────────────────────────
        val ax = event.values[0]   // m/s² along X
        val ay = event.values[1]   // m/s² along Y
        val az = event.values[2]   // m/s² along Z

        // Vector magnitude: √(ax² + ay² + az²)
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()

        // Normalise to multiples of standard gravity
        val gForce = magnitude / STANDARD_GRAVITY

        // ── Step 2: Phase 1 – Free-fall detection ────────────
        if (gForce < FREE_FALL_THRESHOLD_G) {
            if (!freeFallDetected) {
                freeFallDetected = true
                freeFallTimestamp = now
                Log.d(TAG, "⬇ Free-fall detected (gForce=%.2f G)".format(gForce))
            }
            // Stay in free-fall state; don't reset the timestamp
            // so the window is anchored to the *first* low-G reading.
            return
        }

        // ── Step 3: Phase 2 – Impact detection ───────────────
        if (freeFallDetected) {
            val elapsed = now - freeFallTimestamp

            if (elapsed > IMPACT_WINDOW_MS) {
                // Window expired – it wasn't a real fall (phone
                // was just placed down gently or rotated).
                freeFallDetected = false
                Log.d(TAG, "⏱ Impact window expired (${elapsed}ms) – resetting")
                return
            }

            if (gForce > IMPACT_THRESHOLD_G) {
                // ═══════════════════════════════════════════════
                // 🚨  FALL CONFIRMED  🚨
                // Free-fall followed by high-G impact within 1 s.
                // ═══════════════════════════════════════════════
                freeFallDetected = false
                lastTriggerTimestamp = now

                Log.e(
                    TAG,
                    "🚨🚨🚨 FALL DETECTED: free-fall → impact " +
                            "(gForce=%.2f G, elapsed=%d ms) 🚨🚨🚨".format(gForce, elapsed)
                )

                // Broadcast SOS intent to the rest of the app
                val sosIntent = Intent(ACTION_SOS_TRIGGERED).apply {
                    putExtra(EXTRA_REASON, "FALL_DETECTED")
                    putExtra("G_FORCE", gForce)
                    putExtra("ELAPSED_MS", elapsed)
                }
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(sosIntent)

                Log.i(TAG, "SOS broadcast sent – entering ${COOLDOWN_MS}ms cooldown")
            }
            // If gForce is between 0.3 and 2.5 during the window,
            // we just keep waiting for a bigger spike.
        }
    }
}
