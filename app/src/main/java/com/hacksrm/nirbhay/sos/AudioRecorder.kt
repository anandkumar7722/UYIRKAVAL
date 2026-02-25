package com.hacksrm.nirbhay.sos

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Simple MediaRecorder-backed helper to record a 60s audio snippet and save it
 * to the app's external files directory under Music/sos_recordings.
 *
 * Improvements:
 * - Guards against double start() calls
 * - Ensures outputPath is set as early as possible
 * - Uses a single-thread executor for stop scheduling
 * - Returns saved file path on stop(), or null if recording failed
 */
class AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false

    fun start(context: Context, sosTimestamp: Long) {
        if (running) {
            Log.w("AudioRecorder", "start() called but recorder already running")
            return
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sos_recordings")
        if (!dir.exists()) dir.mkdirs()

        val filename = "sos_${sosTimestamp}.m4a"
        val outFile = File(dir, filename)
        outputPath = outFile.absolutePath

        recorder = MediaRecorder()
        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputPath)
                prepare()
                start()
            }
            running = true
            Log.i("AudioRecorder", "Started recording SOS audio: $outputPath")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to prepare/start recorder", e)
            // Clean up on failure
            try {
                recorder?.release()
            } catch (t: Throwable) {
            }
            recorder = null
            outputPath = null
            running = false
            return
        }

        // Schedule stop after 60 seconds on background thread
        executor.execute {
            try {
                Thread.sleep(60_000)
            } catch (ignored: InterruptedException) {
            }
            stop()
        }
    }

    fun stop(): String? {
        if (!running && recorder == null) {
            Log.w("AudioRecorder", "stop() called but recorder not running")
            return null
        }

        try {
            recorder?.apply {
                try {
                    stop()
                } catch (t: Throwable) {
                    Log.w("AudioRecorder", "stop() threw: ${t.message}")
                }
                try {
                    release()
                } catch (t: Throwable) {
                    Log.w("AudioRecorder", "release() threw: ${t.message}")
                }
            }
        } finally {
            recorder = null
            running = false
        }

        val path = outputPath
        if (path == null) {
            Log.w("AudioRecorder", "Stopped recording, but outputPath was null")
            return null
        }

        val f = File(path)
        if (!f.exists() || f.length() == 0L) {
            Log.w("AudioRecorder", "Recorded file missing or empty: $path")
            return null
        }

        Log.i("AudioRecorder", "Stopped recording, file saved: $path")
        return path
    }
}
