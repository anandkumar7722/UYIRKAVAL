package com.hacksrm.nirbhay.sos

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

class FallDetector(private val context: Context, private val onFallDetected: () -> Unit) : SensorEventListener {
    companion object {
        private const val TAG = "FallDetector"
        private const val STANDARD_GRAVITY = 9.80665f
        private const val FREE_FALL_THRESHOLD_G = 0.3f
        private const val IMPACT_THRESHOLD_G = 2.5f
        private const val IMPACT_WINDOW_MS = 1_000L
        private const val COOLDOWN_MS = 10_000L
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var freeFallDetected = false
    private var freeFallTimestamp = 0L
    private var lastTriggerTimestamp = 0L

    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer available")
            return
        }
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        Log.d(TAG, "FallDetector started")
    }

    fun stop() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Error unregistering listener: ${t.message}")
        }
        sensorManager = null
        accelerometer = null
        Log.d(TAG, "FallDetector stopped")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < COOLDOWN_MS) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val gForce = magnitude / STANDARD_GRAVITY

        if (gForce < FREE_FALL_THRESHOLD_G) {
            if (!freeFallDetected) {
                freeFallDetected = true
                freeFallTimestamp = now
                Log.d(TAG, "⬇ Free-fall detected (gForce=%.2f G)".format(gForce))
            }
            return
        }

        if (freeFallDetected) {
            val elapsed = now - freeFallTimestamp
            if (elapsed > IMPACT_WINDOW_MS) {
                freeFallDetected = false
                Log.d(TAG, "⏱ Impact window expired (${elapsed}ms) – resetting")
                return
            }

            if (gForce > IMPACT_THRESHOLD_G) {
                freeFallDetected = false
                lastTriggerTimestamp = now
                Log.e(TAG, "🚨 FALL DETECTED: gForce=%.2f G, elapsed=%d ms".format(gForce, elapsed))

                // Delay briefly to allow UI to respond; call callback
                handler.post {
                    try {
                        onFallDetected()
                    } catch (t: Throwable) {
                        Log.w(TAG, "onFallDetected callback threw: ${t.message}")
                    }
                }
            }
        }
    }
}

