package com.hacksrm.nirbhay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
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
 * detected with confidence > 60 %, the service:
 *   1. Logs a red `Log.e` warning.
 *   2. Sends a local broadcast [ACTION_SOS_TRIGGERED] with
 *      extra "REASON" = "SCREAM_DETECTED".
 *   3. Applies a 5-second cooldown to avoid spam.
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

        /** Foreground notification channel & ID. */
        private const val CHANNEL_ID = "scream_detection_channel"
        private const val NOTIFICATION_ID = 1001

        /** TFLite model filename inside assets/. */
        private const val MODEL_FILE = "yamnet.tflite"

        /**
         * YAMNet labels that indicate distress.
         * These are the exact class names in the model's label map.
         */
        private val DISTRESS_LABELS = setOf(
            "Scream",
            "Yell",
            "Crying, sobbing",
            "Shout",
            "Screaming",
        )

        /** Minimum confidence score (0 – 1) to consider a detection valid. */
        private const val CONFIDENCE_THRESHOLD = 0.60f

        /** Cooldown (ms) after a detection before the next one can fire. */
        private const val COOLDOWN_MS = 5_000L
    }

    // ── State ────────────────────────────────────────────────

    /** Thread-safe flag controlling the inference loop. */
    private val isListening = AtomicBoolean(false)

    /** Background thread that runs the classify loop. */
    private var inferenceThread: Thread? = null

    /** TFLite audio classifier loaded from yamnet.tflite. */
    private var classifier: AudioClassifier? = null

    /**
     * [android.media.AudioRecord] created by the classifier's helper.
     * It is configured with the exact sample-rate and buffer the model
     * expects, so we never have to guess audio parameters.
     */
    private var audioRecord: android.media.AudioRecord? = null

    // ── Service lifecycle ────────────────────────────────────

    /**
     * We are a started (not bound) service – return null.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created – setting up foreground notification")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Called each time the service is started with `startService`/
     * `startForegroundService`.
     *
     * We guard against double-start: if already listening, we just
     * return [START_STICKY] without spawning another thread.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isListening.get()) {
            Log.w(TAG, "Already listening – ignoring duplicate start command")
            return START_STICKY
        }

        Log.i(TAG, "Starting scream detection inference loop")
        startListening()

        // If killed by the OS, restart automatically (no intent re-delivery).
        return START_STICKY
    }

    /**
     * Lifecycle end: stop recording, kill the inference thread, and
     * release native TFLite resources.
     */
    override fun onDestroy() {
        Log.i(TAG, "Service destroyed – releasing resources")
        stopListening()
        super.onDestroy()
    }

    // ── Notification helpers ─────────────────────────────────

    /**
     * Register a low-importance notification channel.
     * Calling this multiple times is safe – the system ignores
     * duplicate channel registrations.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scream Detection",
            NotificationManager.IMPORTANCE_LOW,    // no sound/vibrate
        ).apply {
            description = "Keeps the Edge-AI scream detection service running."
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Build the persistent foreground notification.
     * Uses [NotificationCompat] for backward compat although our
     * minSdk 26 already supports channels natively.
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SHE-SHIELD Active")
            .setContentText("Listening for distress sounds…")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)            // non-dismissible
            .setSilent(true)             // no sound/vibration
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ── Audio recording & inference ──────────────────────────

    /**
     * Load the TFLite model, create an [AudioRecord] from the
     * classifier, start recording, and spin up the inference thread.
     */
    private fun startListening() {
        try {
            // ── 1. Load YAMNet model ──────────────────────────
            // AudioClassifier.createFromFile reads the .tflite from
            // the APK's assets/ folder and prepares the interpreter.
            classifier = AudioClassifier.createFromFile(this, MODEL_FILE)
            Log.i(TAG, "Model loaded: $MODEL_FILE")

            // ── 2. Create AudioRecord from classifier ─────────
            // The classifier knows the required sample rate (16 kHz),
            // channel config (mono), and buffer size.  This is far
            // safer than constructing AudioRecord manually.
            val record = classifier!!.createAudioRecord()
            audioRecord = record

            // ── 3. Create a reusable TensorAudio buffer ───────
            // This buffer is sized to exactly one inference window
            // (0.975 s at 16 kHz = 15 600 samples for YAMNet).
            val tensorAudio = classifier!!.createInputTensorAudio()

            // ── 4. Start the microphone ───────────────────────
            record.startRecording()
            Log.i(TAG, "AudioRecord started – sample rate ${record.sampleRate} Hz")

            // ── 5. Spin up the inference thread ───────────────
            isListening.set(true)
            inferenceThread = Thread({
                runInferenceLoop(tensorAudio)
            }, "ScreamDetection-Inference").apply {
                isDaemon = true   // won't prevent JVM shutdown
                start()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise scream detection", e)
            stopSelf()
        }
    }

    /**
     * The core inference loop.  Runs on a background thread.
     *
     * **How YAMNet inference works (step-by-step):**
     *
     *  1.  `tensorAudio.load(audioRecord)` copies the latest audio
     *      samples from the ring-buffer of [AudioRecord] into the
     *      TensorAudio, resampling / framing as needed.
     *
     *  2.  `classifier.classify(tensorAudio)` runs the YAMNet graph:
     *      - The raw PCM waveform is converted to a log-mel
     *        spectrogram internally.
     *      - The spectrogram is fed through a MobileNet-v1 backbone.
     *      - The output is a probability distribution over 521
     *        AudioSet classes.
     *
     *  3.  We iterate over the returned [Classifications] and check
     *      whether any label in [DISTRESS_LABELS] exceeds
     *      [CONFIDENCE_THRESHOLD].
     *
     *  4.  If a match is found we broadcast an SOS intent and then
     *      sleep for [COOLDOWN_MS] to prevent duplicate triggers.
     *
     *  5.  If no match, we sleep briefly (200 ms) to avoid
     *      burning CPU.  YAMNet's window is ~1 s so polling at
     *      5 Hz gives good overlap without waste.
     */
    private fun runInferenceLoop(
        tensorAudio: org.tensorflow.lite.support.audio.TensorAudio,
    ) {
        Log.i(TAG, "Inference loop started on thread ${Thread.currentThread().name}")

        val record = audioRecord ?: return
        val cls = classifier ?: return

        while (isListening.get()) {
            try {
                // ── Step 1: Load latest audio from the mic buffer ──
                tensorAudio.load(record)

                // ── Step 2: Run YAMNet inference ──
                val results = cls.classify(tensorAudio)

                // ── Step 3: Parse output categories ──
                // results is a List<Classifications>, typically one head.
                // Each Classifications contains a list of Category objects
                // sorted by descending score.
                var detected = false
                var detectedLabel = ""
                var detectedScore = 0f

                for (classifications in results) {
                    for (category in classifications.categories) {
                        val label = category.label
                        val score = category.score

                        // Check if a distress label exceeds threshold
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
                    // 🚨 RED ALERT – distress sound detected
                    Log.e(
                        TAG,
                        "🚨🚨🚨 DISTRESS DETECTED: \"$detectedLabel\" " +
                                "with ${(detectedScore * 100).toInt()}% confidence 🚨🚨🚨"
                    )

                    // Send a LocalBroadcast so any Activity/Fragment
                    // in the same process can react immediately.
                    val sosIntent = Intent(ACTION_SOS_TRIGGERED).apply {
                        putExtra(EXTRA_REASON, "SCREAM_DETECTED")
                        putExtra("LABEL", detectedLabel)
                        putExtra("SCORE", detectedScore)
                    }
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(sosIntent)

                    Log.i(TAG, "SOS broadcast sent – entering ${COOLDOWN_MS}ms cooldown")

                    // ── Cooldown to prevent rapid-fire triggers ──
                    Thread.sleep(COOLDOWN_MS)
                } else {
                    // No distress – yield the CPU briefly.
                    // 200 ms ≈ 5 checks/sec, giving ~80 % overlap
                    // with YAMNet's 0.975 s window.
                    Thread.sleep(200)
                }

            } catch (ie: InterruptedException) {
                // Thread interrupted → exit cleanly
                Log.i(TAG, "Inference thread interrupted – exiting loop")
                break
            } catch (e: Exception) {
                // Log and continue – don't let a single bad frame kill the service
                Log.e(TAG, "Error during inference cycle", e)
                Thread.sleep(500)
            }
        }

        Log.i(TAG, "Inference loop ended")
    }

    // ── Cleanup ──────────────────────────────────────────────

    /**
     * Thread-safe shutdown:
     *  1. Signal the loop to stop.
     *  2. Interrupt the thread (wakes it from any sleep).
     *  3. Stop and release AudioRecord.
     *  4. Close the TFLite classifier (frees native memory).
     */
    private fun stopListening() {
        isListening.set(false)

        // Interrupt the inference thread so it doesn't hang in sleep()
        inferenceThread?.interrupt()
        inferenceThread = null

        // Stop the microphone
        try {
            audioRecord?.stop()
            audioRecord?.release()
            Log.i(TAG, "AudioRecord stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        // Release native TFLite resources
        try {
            classifier?.close()
            Log.i(TAG, "AudioClassifier closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing AudioClassifier", e)
        }
        classifier = null
    }
}
