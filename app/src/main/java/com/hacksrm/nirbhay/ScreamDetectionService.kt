package com.hacksrm.nirbhay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioRecord
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

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

    /** Thread-safe flag controlling the inference loop. */
    private val isListening = AtomicBoolean(false)

    /** Background thread that runs the inference loop. */
    private var inferenceThread: Thread? = null

    /** TFLite audio classifier loaded from yamnet.tflite. */
    private var classifier: AudioClassifier? = null

    /**
     * [AudioRecord] created by the classifier's helper.
     * It is configured with the exact sample-rate and buffer the model
     * expects, so we never have to guess audio parameters.
     */
    private var audioRecord: AudioRecord? = null

    /** Consecutive high-confidence distress frames counter. */
    private var consecutiveHits = 0

    // ── Service lifecycle ────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created – setting up foreground notification")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isListening.get()) {
            Log.w(TAG, "Already listening – ignoring duplicate start command")
            return START_STICKY
        }

        Log.i(TAG, "Starting scream detection inference loop")
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
        try {
            // ── 1. Load YAMNet model ──────────────────────────
            classifier = AudioClassifier.createFromFile(this, MODEL_FILE)
            Log.i(TAG, "✅ Model loaded: $MODEL_FILE")

            // ── 2. Create AudioRecord from classifier ─────────
            val record = classifier!!.createAudioRecord()
            audioRecord = record

            // ── 3. Validate AudioRecord state ─────────────────
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌❌❌ AudioRecord FAILED TO INITIALIZE (state=${record.state}). " +
                        "RECORD_AUDIO permission may be missing or mic is in use by another app.")
                stopSelf()
                return
            }
            Log.i(TAG, "✅ AudioRecord initialized – sampleRate=${record.sampleRate}Hz, " +
                    "channelConfig=${record.channelConfiguration}, " +
                    "audioFormat=${record.audioFormat}")

            // ── 4. Create a reusable TensorAudio buffer ───────
            val tensorAudio = classifier!!.createInputTensorAudio()

            // ── 5. Start the microphone ───────────────────────
            record.startRecording()

            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "❌❌❌ AudioRecord.startRecording() did NOT start. " +
                        "recordingState=${record.recordingState}. Mic may be blocked.")
                stopSelf()
                return
            }
            Log.i(TAG, "✅ AudioRecord is RECORDING – sample rate ${record.sampleRate} Hz")

            // ── 6. Spin up the inference thread ───────────────
            isListening.set(true)
            consecutiveHits = 0
            inferenceThread = Thread({
                runInferenceLoop(tensorAudio)
            }, "ScreamDetection-Inference").apply {
                isDaemon = true
                start()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialise scream detection", e)
            stopSelf()
        }
    }

    /**
     * Compute Root Mean Square (RMS) of a short[] PCM buffer.
     * Returns 0.0 if the buffer is all zeros (mic is muted).
     */
    private fun computeRms(buffer: ShortArray, readCount: Int): Float {
        if (readCount <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until readCount) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / readCount).toFloat()
    }

    /**
     * The core inference loop.  Runs on a background thread.
     *
     * ENHANCED with:
     *  - RMS mic sanity check before every inference cycle
     *  - Consecutive-frame requirement to avoid false positives
     *  - Top-5 label logging for easy debugging
     */
    private fun runInferenceLoop(
        tensorAudio: org.tensorflow.lite.support.audio.TensorAudio,
    ) {
        Log.i(TAG, "🎙 Inference loop started on thread ${Thread.currentThread().name}")
        Log.i(TAG, "🎙 Confidence threshold: ${(CONFIDENCE_THRESHOLD * 100).toInt()}%, " +
                "consecutive frames required: $CONSECUTIVE_FRAMES_REQUIRED")

        val record = audioRecord ?: return
        val cls = classifier ?: return

        // Pre-allocate a raw PCM buffer for the RMS sanity check.
        // YAMNet expects 0.975 s at 16 kHz = 15600 samples.
        val rawBufferSize = record.sampleRate  // 1 second of audio
        val rawBuffer = ShortArray(rawBufferSize)
        var cycleCount = 0L

        while (isListening.get()) {
            try {
                cycleCount++

                // ── Mic Sanity Check ──────────────────────────
                // Read raw PCM and compute RMS to verify the mic
                // is actually capturing audio (not muted by Android).
                val readCount = record.read(rawBuffer, 0, rawBuffer.size)
                val rms = computeRms(rawBuffer, readCount)

                if (cycleCount % 10 == 1L) {
                    // Log RMS every ~10 cycles to avoid log spam
                    Log.d(TAG, "🎤 Mic RMS: %.1f | readCount: %d | cycle: %d".format(rms, readCount, cycleCount))
                }

                if (readCount <= 0) {
                    Log.e(TAG, "❌❌❌ AudioRecord.read() returned $readCount — " +
                            "mic is NOT delivering audio data!")
                    Thread.sleep(500)
                    continue
                }

                if (rms < SILENT_RMS_THRESHOLD) {
                    if (cycleCount % 5 == 1L) {
                        Log.w(TAG, "⚠️ VERY LOW MIC RMS: %.1f — ANDROID MAY BE MUTING THE MIC. ".format(rms) +
                                "Check: 1) RECORD_AUDIO granted at runtime  " +
                                "2) No other app holding the mic  " +
                                "3) foregroundServiceType=\"microphone\" in manifest")
                    }
                    // Don't skip inference — let the model see silence so we
                    // don't get stuck. But reset consecutive hits.
                    consecutiveHits = 0
                }

                // ── Step 1: Load latest audio into TensorAudio ──
                tensorAudio.load(record)

                // ── Step 2: Run YAMNet inference ──
                val results = cls.classify(tensorAudio)

                // ── Step 3: Parse output categories ──
                var detected = false
                var detectedLabel = ""
                var detectedScore = 0f

                for (classifications in results) {
                    // Log top-5 labels every 15 cycles for debugging
                    if (cycleCount % 15 == 1L) {
                        val top5 = classifications.categories
                            .sortedByDescending { it.score }
                            .take(5)
                            .joinToString { "${it.label}=${(it.score * 100).toInt()}%" }
                        Log.d(TAG, "📊 Top-5: $top5")
                    }

                    for (category in classifications.categories) {
                        val label = category.label
                        val score = category.score

                        if (label in DISTRESS_LABELS && score > CONFIDENCE_THRESHOLD) {
                            detected = true
                            detectedLabel = label
                            detectedScore = score
                            break
                        }
                    }
                    if (detected) break
                }

                // ── Step 4: Fire SOS or sleep ──
                if (detected) {
                    consecutiveHits++
                    Log.w(TAG, "🔶 Distress candidate: \"$detectedLabel\" " +
                            "${(detectedScore * 100).toInt()}% " +
                            "(hit $consecutiveHits/$CONSECUTIVE_FRAMES_REQUIRED)")

                    if (consecutiveHits >= CONSECUTIVE_FRAMES_REQUIRED) {
                        // 🚨 CONFIRMED – enough consecutive frames
                        Log.e(
                            TAG,
                            "🚨🚨🚨 DISTRESS CONFIRMED: \"$detectedLabel\" " +
                                    "with ${(detectedScore * 100).toInt()}% confidence " +
                                    "($consecutiveHits consecutive frames) 🚨🚨🚨"
                        )

                        val sosIntent = Intent(ACTION_SOS_TRIGGERED).apply {
                            putExtra(EXTRA_REASON, "SCREAM_DETECTED")
                            putExtra("LABEL", detectedLabel)
                            putExtra("SCORE", detectedScore)
                        }
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(sosIntent)

                        Log.i(TAG, "✅ SOS broadcast sent – entering ${COOLDOWN_MS}ms cooldown")
                        consecutiveHits = 0

                        Thread.sleep(COOLDOWN_MS)
                    } else {
                        // Wait a short time before next frame check
                        Thread.sleep(200)
                    }
                } else {
                    consecutiveHits = 0
                    Thread.sleep(200)
                }

            } catch (ie: InterruptedException) {
                Log.i(TAG, "Inference thread interrupted – exiting loop")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error during inference cycle", e)
                Thread.sleep(500)
            }
        }

        Log.i(TAG, "Inference loop ended")
    }

    // ── Cleanup ──────────────────────────────────────────────

    private fun stopListening() {
        isListening.set(false)

        inferenceThread?.interrupt()
        inferenceThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            Log.i(TAG, "AudioRecord stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        try {
            classifier?.close()
            Log.i(TAG, "AudioClassifier closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing AudioClassifier", e)
        }
        classifier = null
    }
}
