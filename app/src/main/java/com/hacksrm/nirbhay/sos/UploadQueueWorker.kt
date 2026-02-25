package com.hacksrm.nirbhay.sos

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.UUID

/**
 * WorkManager worker that retries uploading any pending SosEventEntity rows.
 * Runs whenever network becomes available (triggered by NetworkCallback in MainActivity)
 * or on the 15-minute periodic schedule.
 *
 * For each unuploaded event:
 *  1. POST JSON to /api/sos/trigger (the SOS alert itself)
 *  2. If audioFilePath is set AND the file exists → POST multipart to /api/sos/media (audio evidence)
 * All results are logged clearly so you can trace them in logcat.
 */
class UploadQueueWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "UploadQueueWorker"
        private const val HARDCODED_TOKEN = "tok_demo_123456"
        private const val HARDCODED_VICTIM_ID = "00000000-0000-0000-0000-000000000001"
        private const val BASE_URL = "https://nirbhay-467822196904.asia-south1.run.app/"
    }

    private val db = AppDatabase.getInstance(appContext)

    // Retrofit with HTTP body logging
    private val api: SosApi by lazy {
        val logging = HttpLoggingInterceptor { msg -> Log.i("OkHttp-Worker", msg) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SosApi::class.java)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔄 ─── UploadQueueWorker started ───")
        try {
            val dao = db.sosEventDao()
            val pending = dao.getUnuploaded()

            Log.i(TAG, "🔄 Found ${pending.size} unuploaded SOS event(s) in Room")

            if (pending.isEmpty()) {
                Log.i(TAG, "🔄 Nothing to upload — worker done")
                return@withContext Result.success()
            }

            var successCount = 0
            var failCount = 0

            for (event in pending) {
                Log.i(TAG, "📤 Processing Room event id=${event.id} | method=${event.triggerMethod} | time=${event.timestamp}")

                // ── STEP 1: Upload the SOS event itself via JSON ────────
                var backendSosId: String? = null
                val jsonBody = SosCreateRequest(
                    victim_id       = event.victimId,
                    emergency_token = HARDCODED_TOKEN,
                    lat             = event.lat,
                    lng             = event.lng,
                    trigger_method  = event.triggerMethod,
                    risk_score      = event.riskScore,
                    battery_level   = event.batteryLevel,
                    timestamp       = event.timestamp,
                    audio_file_path = null
                )

                try {
                    Log.i(TAG, "📤 Step 1: POST /api/sos/trigger (JSON)…")
                    val triggerResp = api.trigger(jsonBody)

                    if (triggerResp.isSuccessful) {
                        val body = triggerResp.body()
                        backendSosId = body?.sos_id
                        Log.i(TAG, "✅ SOS trigger SUCCESS → sos_id=$backendSosId is_new=${body?.is_new}")
                    } else {
                        val code   = triggerResp.code()
                        val errMsg = try { triggerResp.errorBody()?.string() } catch (_: Exception) { null }
                        Log.w(TAG, "❌ SOS trigger FAILED: HTTP $code — $errMsg")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ SOS trigger exception: ${e.message}")
                }

                // ── STEP 2: Upload audio via /api/sos/media (if file exists) ──
                val audioPath = event.audioFilePath
                val audioFile = if (!audioPath.isNullOrEmpty()) File(audioPath) else null
                var audioUploaded = false

                if (audioFile != null && audioFile.exists() && audioFile.length() > 0L) {
                    // Use the backend sos_id if we got one, otherwise generate random UUID
                    val mediaSosId = backendSosId ?: UUID.randomUUID().toString()

                    Log.i(TAG, "🎙️ Step 2: POST /api/sos/media (audio upload)")
                    Log.i(TAG, "🎙️ sos_id for media: $mediaSosId")
                    Log.i(TAG, "🎙️ Audio file: $audioPath")
                    Log.i(TAG, "🎙️ Audio file size: ${audioFile.length()} bytes (${audioFile.length() / 1024} KB)")

                    try {
                        fun strPart(value: String) = value.toRequestBody("text/plain".toMediaTypeOrNull())
                        val audioBody = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                        val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, audioBody)

                        val mediaResp = api.uploadMedia(
                            sosId    = strPart(mediaSosId),
                            victimId = strPart(event.victimId),
                            audio    = audioPart
                        )

                        if (mediaResp.isSuccessful) {
                            val mediaBody = mediaResp.body()
                            Log.i(TAG, "✅ Audio upload SUCCESS to /api/sos/media")
                            Log.i(TAG, "✅ sos_id=${mediaBody?.sos_id}")
                            Log.i(TAG, "✅ audio_url=${mediaBody?.audio_url}")
                            audioUploaded = true
                        } else {
                            val code   = mediaResp.code()
                            val errMsg = try { mediaResp.errorBody()?.string() } catch (_: Exception) { null }
                            Log.w(TAG, "❌ Audio upload FAILED: HTTP $code — $errMsg")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Audio upload exception: ${e.message}")
                    }
                } else if (!audioPath.isNullOrEmpty()) {
                    Log.w(TAG, "⚠️ Audio path set ($audioPath) but file missing or empty")
                } else {
                    Log.i(TAG, "📤 No audio file for this event — skipping /api/sos/media")
                }

                // ── Mark as uploaded if SOS trigger succeeded ────────────
                val triggerOk = backendSosId != null
                if (triggerOk) {
                    dao.markUploaded(event.id)
                    successCount++
                    Log.i(TAG, "✅ Room event id=${event.id} marked as uploaded" +
                            if (audioUploaded) " (audio included)" else " (no audio)")
                } else {
                    failCount++
                    Log.w(TAG, "❌ Room event id=${event.id} will be retried on next WorkManager run")
                }
            }

            Log.i(TAG, "🔄 ─── UploadQueueWorker done: $successCount succeeded, $failCount failed ───")
            if (failCount > 0) Result.retry() else Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ UploadQueueWorker crashed: ${e.message}")
            Result.retry()
        }
    }
}
