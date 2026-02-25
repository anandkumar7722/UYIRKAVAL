package com.hacksrm.nirbhay.sos

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class ScreamDetector(private val context: Context, private val onScreamDetected: (label: String, score: Float) -> Unit) {
    companion object {
        private const val TAG = "ScreamDetector"
        private const val MODEL_FILE = "yamnet.tflite"
        private val DISTRESS_LABELS = setOf("Scream", "Screaming", "Yell", "Shout", "Crying, sobbing")
        private const val CONFIDENCE_THRESHOLD = 0.30f
        private const val CONSECUTIVE_FRAMES_REQUIRED = 2
        private const val SILENT_RMS_THRESHOLD = 10f
    }

    private var classifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private val isListening = AtomicBoolean(false)
    private var inferenceJob: Job? = null

    fun start() {
        try {
            classifier = AudioClassifier.createFromFile(context, MODEL_FILE)
            Log.i(TAG, "Model loaded: $MODEL_FILE")
            val record = classifier!!.createAudioRecord()
            audioRecord = record
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                stop()
                return
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord did not start recording")
                stop()
                return
            }

            val tensorAudio = classifier!!.createInputTensorAudio()
            isListening.set(true)
            inferenceJob = CoroutineScope(Dispatchers.Default).launch {
                var consecutiveHits = 0
                val rawBufferSize = record.sampleRate
                val rawBuffer = ShortArray(rawBufferSize)
                var cycleCount = 0L

                while (isActive && isListening.get()) {
                    cycleCount++
                    val readCount = record.read(rawBuffer, 0, rawBuffer.size)
                    val rms = computeRms(rawBuffer, readCount)
                    if (readCount <= 0) {
                        kotlinx.coroutines.delay(500)
                        continue
                    }

                    if (rms < SILENT_RMS_THRESHOLD) {
                        consecutiveHits = 0
                    }

                    tensorAudio.load(record)
                    val results = classifier!!.classify(tensorAudio)

                    var detected = false
                    var detectedLabel = ""
                    var detectedScore = 0f

                    for (classifications in results) {
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

                    if (detected) {
                        consecutiveHits++
                        Log.w(TAG, "Distress candidate: $detectedLabel ${(detectedScore*100).toInt()}% (hit $consecutiveHits)")
                        if (consecutiveHits >= CONSECUTIVE_FRAMES_REQUIRED) {
                            onScreamDetected(detectedLabel, detectedScore)
                            consecutiveHits = 0
                            kotlinx.coroutines.delay(5000) // cooldown
                        }
                    } else {
                        consecutiveHits = 0
                        kotlinx.coroutines.delay(200)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreamDetector", e)
            stop()
        }
    }

    fun stop() {
        try {
            isListening.set(false)
            inferenceJob?.cancel()
            inferenceJob = null
            audioRecord?.run {
                try { stop() } catch (t: Throwable) {}
                try { release() } catch (t: Throwable) {}
            }
            audioRecord = null
            classifier?.close()
            classifier = null
            Log.i(TAG, "ScreamDetector stopped")
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping ScreamDetector: ${t.message}")
        }
    }

    private fun computeRms(buffer: ShortArray, readCount: Int): Float {
        if (readCount <= 0) return 0f
        var sum = 0.0
        for (i in 0 until readCount) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        return kotlin.math.sqrt(sum / readCount).toFloat()
    }
}

