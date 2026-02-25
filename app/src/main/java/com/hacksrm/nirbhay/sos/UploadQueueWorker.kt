package com.hacksrm.nirbhay.sos

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Worker that uploads pending SosEventEntity rows to the real backend.
 */
class UploadQueueWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val db = AppDatabase.getInstance(appContext)
    private val HARDCODED_TOKEN = "tok_demo_123456" // change as needed

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = db.sosEventDao()
            val pending = dao.getUnuploaded()
            if (pending.isEmpty()) return@withContext Result.success()

            // Retrofit to real backend
            val retrofit = Retrofit.Builder()
                .baseUrl("https://nirbhay-467822196904.asia-south1.run.app/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(SosApi::class.java)

            for (event in pending) {
                val resp = if (!event.audioFilePath.isNullOrEmpty()) {
                    // Prepare multipart upload
                    val file = java.io.File(event.audioFilePath)
                    val audioPart = if (file.exists()) {
                        val mediaType = "audio/m4a".toMediaTypeOrNull()
                        val reqFile = file.asRequestBody(mediaType)
                        okhttp3.MultipartBody.Part.createFormData("audio", file.name, reqFile)
                    } else null

                    fun strPart(value: String) = value.toRequestBody("text/plain".toMediaTypeOrNull())

                    try {
                        api.triggerMultipart(
                            victimId = strPart(event.victimId),
                            emergencyToken = strPart(HARDCODED_TOKEN),
                            lat = strPart(event.lat.toString()),
                            lng = strPart(event.lng.toString()),
                            triggerMethod = strPart(event.triggerMethod),
                            riskScore = strPart(event.riskScore.toString()),
                            batteryLevel = strPart(event.batteryLevel.toString()),
                            timestamp = strPart(event.timestamp.toString()),
                            audio = audioPart
                        )
                    } catch (e: Exception) {
                        Log.w("UploadQueueWorker", "Multipart upload failed", e)
                        null
                    }
                } else {
                    // No audio file; fall back to JSON body
                    val body = SosCreateRequest(
                        victim_id = event.victimId,
                        emergency_token = HARDCODED_TOKEN,
                        lat = event.lat,
                        lng = event.lng,
                        trigger_method = event.triggerMethod,
                        risk_score = event.riskScore,
                        battery_level = event.batteryLevel,
                        timestamp = event.timestamp,
                        audio_file_path = null
                    )

                    try {
                        api.trigger(body)
                    } catch (e: Exception) {
                        Log.w("UploadQueueWorker", "HTTP JSON call failed", e)
                        null
                    }
                }

                if (resp != null && resp.isSuccessful) {
                    dao.markUploaded(event.id)
                    Log.d("UploadQueueWorker", "Uploaded SosEvent id=${event.id}")
                } else {
                    Log.w("UploadQueueWorker", "Upload failed for id=${event.id} code=${resp?.code()}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("UploadQueueWorker", "Worker failed", e)
            Result.retry()
        }
    }
}
