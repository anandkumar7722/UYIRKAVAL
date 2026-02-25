package com.hacksrm.nirbhay.sos

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Simple MediaRecorder-backed helper to record a 60s audio snippet and save it
 * to the app's external files directory under Music/sos_recordings.
 *
 * Key design:
 * - start() kicks off an internal 60s timer that auto-stops recording and sets savedPath
 * - stop() can be called externally at any time; if the recorder already auto-stopped,
 *   it still returns the saved file path (not null) so the caller can upload it
 * - outputPath tracks the intended file path (set immediately in start())
 * - savedPath tracks the confirmed saved path (set only after successful stop())
 */
class AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null       // set in start(), used to construct file
    @Volatile private var savedPath: String? = null  // set after successful stop()
    @Volatile private var running = false
    private val executor = Executors.newSingleThreadExecutor()

    /** Returns the intended output file path (available immediately after start()). */
    fun currentPath(): String? = outputPath

    fun start(context: Context, sosTimestamp: Long) {
        if (running) {
            Log.w("AudioRecorder", "start() called but recorder already running — ignoring")
            return
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sos_recordings")
        if (!dir.exists()) dir.mkdirs()

        val filename = "sos_${sosTimestamp}.m4a"
        val outFile = File(dir, filename)
        outputPath = outFile.absolutePath
        savedPath = null  // clear any previous save

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
            Log.i("AudioRecorder", "▶️ Started recording: $outputPath")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recorder: ${e.message}", e)
            try { recorder?.release() } catch (_: Throwable) {}
            recorder = null
            outputPath = null
            running = false
            return
        }

        // Internal 60-second auto-stop — saves the path into savedPath when done
        executor.execute {
            try {
                Thread.sleep(60_000)
            } catch (_: InterruptedException) {}
            stopInternal()
        }
    }

    /**
     * Internal stop called by the executor after 60 s.
     * Sets savedPath on success.
     */
    private fun stopInternal() {
        if (!running) return  // already stopped externally
        doStop()
    }

    /**
     * External stop called by SOSEngine after waiting 61 s.
     * Always returns the saved file path if the file exists — even if the internal
     * executor already stopped the recorder first.
     */
    fun stop(): String? {
        // If still running, stop it now
        if (running) {
            doStop()
        } else {
            Log.d("AudioRecorder", "stop() called — recorder already auto-stopped internally")
        }

        // Return savedPath regardless of who stopped it
        val path = savedPath
        if (path != null) {
            val f = File(path)
            if (f.exists() && f.length() > 0L) {
                Log.i("AudioRecorder", "✅ stop() returning saved path: $path (${f.length()} bytes)")
                return path
            } else {
                Log.w("AudioRecorder", "⚠️ stop() — file missing or empty at: $path")
            }
        } else {
            Log.w("AudioRecorder", "⚠️ stop() — savedPath is null (recording may have failed)")
        }
        return null
    }

    /** Shared stop/release logic used by both internal and external stop. */
    private fun doStop() {
        val path = outputPath
        try {
            recorder?.apply {
                try { stop() } catch (t: Throwable) { Log.w("AudioRecorder", "recorder.stop() threw: ${t.message}") }
                try { release() } catch (t: Throwable) { Log.w("AudioRecorder", "recorder.release() threw: ${t.message}") }
            }
        } finally {
            recorder = null
            running = false
        }

        // Validate the file and set savedPath
        if (path != null) {
            val f = File(path)
            if (f.exists() && f.length() > 0L) {
                savedPath = path
                Log.i("AudioRecorder", "⏹️ Recording stopped, file saved: $path (${f.length()} bytes)")
            } else {
                savedPath = null
                Log.w("AudioRecorder", "⏹️ Recording stopped but file missing/empty: $path")
            }
        } else {
            savedPath = null
            Log.w("AudioRecorder", "⏹️ Recording stopped but outputPath was null")
        }
    }
}
