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
        private const val BASE_URL = "https://nirbhay-5gcekoejfa-el.a.run.app"
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
                    lat             = event.lat,
                    lng             = event.lng,
                    trigger_method  = event.triggerMethod,
                    risk_score      = event.riskScore,
                    battery_level   = event.batteryLevel
                )

                var triggerSuccess = false
                try {
                    Log.i(TAG, "📤 Step 1: POST /api/sos/trigger (JSON)…")
                    val triggerResp = api.trigger(jsonBody)

                    if (triggerResp.isSuccessful) {
                        val body = triggerResp.body()
                        backendSosId = body?.sos_id
                        triggerSuccess = true  // Mark success on ANY 2xx
                        Log.i(TAG, "✅ SOS trigger SUCCESS → sos_id=$backendSosId is_new=${body?.is_new}")
                    } else {
                        val code   = triggerResp.code()
                        val errMsg = try { triggerResp.errorBody()?.string() } catch (_: Exception) { null }
                        Log.w(TAG, "❌ SOS trigger FAILED: HTTP $code — $errMsg")
                        // 4xx errors (except 429) are permanent failures — mark uploaded to stop retrying
                        if (code in 400..499 && code != 429) {
                            triggerSuccess = true
                            Log.w(TAG, "⚠️ Permanent failure ($code) — marking as uploaded to stop retries")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ SOS trigger exception: ${e.message}")
                }

                // ── STEP 2: Upload media (audio + photos) via /api/sos/media ──
                val audioPath = event.audioFilePath
                val audioFile = if (!audioPath.isNullOrEmpty()) File(audioPath) else null
                var mediaUploaded = false

                // Collect any local SOS photos from the sos_photos directory
                val photoDir = File(applicationContext.getExternalFilesDir(null), "sos_photos")
                val photoFiles = if (photoDir.exists()) {
                    photoDir.listFiles { f -> f.extension.equals("jpg", true) || f.extension.equals("jpeg", true) || f.extension.equals("png", true) }
                        ?.filter { it.length() > 0 }
                        ?.take(10)
                        ?.toList() ?: emptyList()
                } else emptyList()

                val hasAudio = audioFile != null && audioFile.exists() && audioFile.length() > 0L
                val hasPhotos = photoFiles.isNotEmpty()

                if (hasAudio || hasPhotos) {
                    val mediaSosId = backendSosId ?: UUID.randomUUID().toString()

                    Log.i(TAG, "📤 Step 2: POST /api/sos/media (multipart)")
                    Log.i(TAG, "📤 sos_id for media: $mediaSosId")
                    Log.i(TAG, "📤 Audio: ${if (hasAudio) "$audioPath (${audioFile!!.length() / 1024} KB)" else "none"}")
                    Log.i(TAG, "📤 Photos: ${photoFiles.size} file(s)")

                    try {
                        fun strPart(value: String) = value.toRequestBody("text/plain".toMediaTypeOrNull())

                        // Build image parts
                        val imageParts = photoFiles.mapIndexed { i, file ->
                            Log.i(TAG, "📤   image[$i]: ${file.name} (${file.length() / 1024} KB)")
                            val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                            MultipartBody.Part.createFormData("images", file.name, body)
                        }

                        // Build audio part (or null)
                        val audioPart = if (hasAudio) {
                            val body = audioFile!!.asRequestBody("audio/m4a".toMediaTypeOrNull())
                            MultipartBody.Part.createFormData("audio", audioFile.name, body)
                        } else null

                        val mediaResp = api.uploadMedia(
                            sosId    = strPart(mediaSosId),
                            victimId = strPart(event.victimId),
                            images   = imageParts,
                            audio    = audioPart
                        )

                        if (mediaResp.isSuccessful) {
                            val mediaBody = mediaResp.body()
                            Log.i(TAG, "✅ Media upload SUCCESS to /api/sos/media")
                            Log.i(TAG, "✅ sos_id=${mediaBody?.sos_id}")
                            Log.i(TAG, "✅ audio_url=${mediaBody?.audio_url ?: "none"}")
                            Log.i(TAG, "✅ image_urls=${mediaBody?.image_urls?.joinToString() ?: "none"}")
                            mediaUploaded = true
                        } else {
                            val code   = mediaResp.code()
                            val errMsg = try { mediaResp.errorBody()?.string() } catch (_: Exception) { null }
                            Log.w(TAG, "❌ Media upload FAILED: HTTP $code — $errMsg")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Media upload exception: ${e.message}")
                    }
                } else if (!audioPath.isNullOrEmpty()) {
                    Log.w(TAG, "⚠️ Audio path set ($audioPath) but file missing or empty, and no photos found")
                } else {
                    Log.i(TAG, "📤 No audio/photos for this event — skipping /api/sos/media")
                }

                // ── Mark as uploaded if SOS trigger succeeded ────────────
                if (triggerSuccess) {
                    dao.markUploaded(event.id)
                    successCount++
                    Log.i(TAG, "✅ Room event id=${event.id} marked as uploaded" +
                            if (mediaUploaded) " (media included)" else " (no media)")
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
