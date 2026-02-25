package com.hacksrm.nirbhay.sos

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hacksrm.nirbhay.SOSPacket
import com.hacksrm.nirbhay.BridgefyMesh
import com.hacksrm.nirbhay.LocationHelper
import com.hacksrm.nirbhay.connectivity.ConnectivityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

/**
 * Central SOS engine.
 *
 * Flow when SOS is triggered:
 *  1. Cooldown guard (10 s)
 *  2. Save SosEventEntity to Room immediately (never lose the event)
 *  3. POST JSON to /api/sos/trigger (online) OR send Bridgefy mesh packet (offline)
 *  4. In parallel: stop ScreamDetector → wait 500ms → start AudioRecorder for 60 s
 *  5. After recording finishes: update Room row with audioFilePath
 *  6. If online: upload audio file via multipart to /api/sos/trigger
 *     Else: WorkManager will retry upload when connectivity returns
 */
object SOSEngine {
    private const val TAG = "SOSEngine"
    const val HARDCODED_USER_UUID   = "00000000-0000-0000-0000-000000000001"
    const val HARDCODED_EMERGENCY_TOKEN = "tok_demo_123456"

    private val mutex = Mutex()
    private var lastTriggerMs = 0L
    private const val COOLDOWN_MS = 10_000L
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Retrofit instance (shared, lazy) ─────────────────────
    private val retrofitApi: SosApi by lazy {
        val logging = HttpLoggingInterceptor { msg -> Log.i("OkHttp-SOS", msg) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://nirbhay-467822196904.asia-south1.run.app/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SosApi::class.java)
    }

    // ─────────────────────────────────────────────────────────
    // PUBLIC: primary SOS trigger
    // ─────────────────────────────────────────────────────────
    suspend fun triggerSOS(source: TriggerSource, context: Context) {
        val now = System.currentTimeMillis()

        // Cooldown guard
        mutex.withLock {
            if (now - lastTriggerMs < COOLDOWN_MS) {
                Log.d(TAG, "⏳ SOS in cooldown (${COOLDOWN_MS/1000}s), ignoring trigger from $source")
                return
            }
            lastTriggerMs = now
        }

        Log.i(TAG, "🚨 ==================== SOS TRIGGERED ====================")
        Log.i(TAG, "🚨 Source: $source  |  Time: $now")

        // Get GPS
        val (lat, lng) = LocationHelper.getLatLng() ?: Pair(0.0, 0.0)
        Log.i(TAG, "📍 Location: lat=$lat, lng=$lng")

        // ── STEP 1: Persist to Room ──────────────────────────
        val db  = AppDatabase.getInstance(context)
        val dao = db.sosEventDao()
        val triggerMethod = source.name.lowercase()
        val batteryLevel  = getBatteryLevel(context)
        val riskScore     = RiskScoreEngine.currentScore()

        val sosEntity = SosEventEntity(
            victimId      = HARDCODED_USER_UUID,
            lat           = lat,
            lng           = lng,
            triggerMethod = triggerMethod,
            riskScore     = riskScore,
            batteryLevel  = batteryLevel,
            timestamp     = now,
            uploaded      = false,
            audioFilePath = null
        )
        val localId = dao.insert(sosEntity)
        Log.i(TAG, "💾 SosEvent persisted to Room with id=$localId")

        // ── STEP 2: Online vs Offline path ───────────────────
        val online = ConnectivityHelper.isOnline(context)
        Log.i(TAG, "🌐 Connectivity: online=$online")

        var sosId: String? = null   // will hold backend sos_id for audio upload

        if (online) {
            Log.i(TAG, "☁️ ONLINE path → POST /api/sos/trigger")
            sosId = postJsonToBackend(sosEntity, localId, dao, useRelay = false)
        } else {
            Log.i(TAG, "📡 OFFLINE path → Bridgefy mesh broadcast")
            sendViaMesh(sosEntity)
        }

        // ── STEP 3: Audio recording (always, in parallel) ────
        // We launch this independently so it doesn't block the SOS send
        val capturedSosId = sosId
        scope.launch {
            Log.i(TAG, "🎙️ ─── AUDIO RECORDING START ───")
            Log.i(TAG, "🎙️ Stopping ScreamDetector to free microphone…")

            // Stop scream detector so it releases AudioRecord's mic lock
            try {
                val stopIntent = Intent(context, com.hacksrm.nirbhay.ScreamDetectionService::class.java)
                context.stopService(stopIntent)
                Log.i(TAG, "🎙️ ScreamDetectionService stopped")
            } catch (t: Throwable) {
                Log.w(TAG, "🎙️ Could not stop ScreamDetectionService: ${t.message}")
            }

            // Give the OS 600 ms to reclaim the mic
            kotlinx.coroutines.delay(600)

            // Start recording
            val recorder = AudioRecorder()
            recorder.start(context, now)
            Log.i(TAG, "🎙️ AudioRecorder.start() called — recording 60 s to local storage")
            Log.i(TAG, "🎙️ File will be at: ${recorder.currentPath()}")

            // Wait for the full 60-second recording
            kotlinx.coroutines.delay(61_000)

            // Stop and get path
            val audioPath = recorder.stop()
            Log.i(TAG, "🎙️ AudioRecorder.stop() called — result path: $audioPath")

            if (audioPath != null) {
                val audioFile = File(audioPath)
                Log.i(TAG, "🎙️ Audio file saved locally: $audioPath")
                Log.i(TAG, "🎙️ Audio file size: ${audioFile.length()} bytes")

                // Update Room with audio path
                val updated = sosEntity.copy(id = localId, audioFilePath = audioPath)
                dao.update(updated)
                Log.i(TAG, "💾 Room updated: SosEvent id=$localId now has audioFilePath=$audioPath")

                // Upload audio if online
                val onlineNow = ConnectivityHelper.isOnline(context)
                Log.i(TAG, "🌐 Post-recording connectivity check: online=$onlineNow")

                if (onlineNow) {
                    Log.i(TAG, "☁️ Uploading audio file to backend…")
                    uploadAudioToBackend(
                        context       = context,
                        audioFile     = audioFile,
                        sosEntity     = sosEntity.copy(id = localId, audioFilePath = audioPath),
                        localId       = localId,
                        dao           = dao,
                        sosId         = capturedSosId
                    )
                } else {
                    Log.i(TAG, "📡 No internet after recording — audio will be uploaded by WorkManager when connectivity returns")
                }
            } else {
                Log.w(TAG, "🎙️ Recording failed or produced empty file — no audio to upload")
            }

            // Restart ScreamDetector
            try {
                val startIntent = Intent(context, com.hacksrm.nirbhay.ScreamDetectionService::class.java)
                context.startForegroundService(startIntent)
                Log.i(TAG, "🎙️ ScreamDetectionService restarted after recording")
            } catch (t: Throwable) {
                Log.w(TAG, "🎙️ Could not restart ScreamDetectionService: ${t.message}")
            }

            Log.i(TAG, "🎙️ ─── AUDIO RECORDING COMPLETE ───")
        }

        Log.i(TAG, "🚨 ==================== SOS DISPATCH DONE ====================")
        RiskScoreEngine.resetScore()
    }

    // ─────────────────────────────────────────────────────────
    // POST JSON body (no audio) — immediate SOS notification
    // Returns backend sos_id on success, null on failure
    // ─────────────────────────────────────────────────────────
    private suspend fun postJsonToBackend(
        sosEntity  : SosEventEntity,
        localId    : Long,
        dao        : SosEventDao,
        useRelay   : Boolean
    ): String? {
        return try {
            val req = SosCreateRequest(
                victim_id       = sosEntity.victimId,
                emergency_token = HARDCODED_EMERGENCY_TOKEN,
                lat             = sosEntity.lat,
                lng             = sosEntity.lng,
                trigger_method  = sosEntity.triggerMethod,
                risk_score      = sosEntity.riskScore,
                battery_level   = sosEntity.batteryLevel,
                timestamp       = sosEntity.timestamp,
                audio_file_path = null
            )

            val endpoint = if (useRelay) "/api/sos/relay" else "/api/sos/trigger"
            Log.i(TAG, "📤 HTTP POST $endpoint")
            Log.i(TAG, "📤 Payload → victim_id=${req.victim_id} lat=${req.lat} lng=${req.lng} " +
                    "method=${req.trigger_method} risk=${req.risk_score} battery=${req.battery_level}")

            val resp = if (useRelay) retrofitApi.relay(req) else retrofitApi.trigger(req)

            if (resp.isSuccessful) {
                val body = resp.body()
                Log.i(TAG, "✅ Backend accepted SOS → sos_id=${body?.sos_id} is_new=${body?.is_new}")
                dao.markUploaded(localId)
                body?.sos_id
            } else {
                val errBody = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                Log.w(TAG, "❌ Backend rejected SOS: HTTP ${resp.code()} — $errBody")
                Log.w(TAG, "📡 Falling back to Bridgefy mesh")
                sendViaMesh(sosEntity)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network exception posting SOS: ${e.message}")
            Log.w(TAG, "📡 Falling back to Bridgefy mesh after exception")
            sendViaMesh(sosEntity)
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // Upload audio file via multipart to POST /api/sos/media
    // Uses a random sos_id so each upload is unique in the DB
    // ─────────────────────────────────────────────────────────
    private suspend fun uploadAudioToBackend(
        context   : Context,
        audioFile : File,
        sosEntity : SosEventEntity,
        localId   : Long,
        dao       : SosEventDao,
        sosId     : String?
    ) {
        try {
            // Generate a random UUID for sos_id so each media upload is unique
            val mediaSosId = sosId ?: java.util.UUID.randomUUID().toString()

            Log.i(TAG, "📤 ─── AUDIO UPLOAD START ───")
            Log.i(TAG, "📤 Endpoint: POST /api/sos/media (multipart)")
            Log.i(TAG, "📤 sos_id: $mediaSosId")
            Log.i(TAG, "📤 victim_id: ${sosEntity.victimId}")
            Log.i(TAG, "📤 File: ${audioFile.absolutePath}")
            Log.i(TAG, "📤 Size: ${audioFile.length()} bytes (${audioFile.length() / 1024} KB)")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "📤 Audio file missing or empty — skipping upload")
                return
            }

            fun strPart(value: String) =
                value.toRequestBody("text/plain".toMediaTypeOrNull())

            val audioBody = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, audioBody)

            Log.i(TAG, "📤 Sending multipart POST /api/sos/media …")
            val resp = retrofitApi.uploadMedia(
                sosId    = strPart(mediaSosId),
                victimId = strPart(sosEntity.victimId),
                audio    = audioPart
            )

            if (resp.isSuccessful) {
                val body = resp.body()
                Log.i(TAG, "✅ ─── AUDIO UPLOAD SUCCESS ───")
                Log.i(TAG, "✅ Backend sos_id=${body?.sos_id}")
                Log.i(TAG, "✅ audio_url=${body?.audio_url}")
                Log.i(TAG, "✅ Audio file successfully delivered to /api/sos/media")
                dao.markUploaded(localId)
            } else {
                val errBody = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                Log.w(TAG, "❌ ─── AUDIO UPLOAD FAILED ───")
                Log.w(TAG, "❌ HTTP ${resp.code()} — $errBody")
                Log.w(TAG, "❌ Audio remains in Room (id=$localId) for WorkManager retry")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio upload exception: ${e.message}")
            Log.w(TAG, "❌ Audio remains in Room (id=$localId) for WorkManager retry")
        }
        Log.i(TAG, "📤 ─── AUDIO UPLOAD DONE ───")
    }

    // ─────────────────────────────────────────────────────────
    // PUBLIC: expose uploadAudioToBackend for UploadQueueWorker
    // ─────────────────────────────────────────────────────────
    suspend fun uploadAudioForEvent(
        context   : Context,
        audioFile : File,
        sosEntity : SosEventEntity,
        localId   : Long,
        dao       : SosEventDao
    ) {
        uploadAudioToBackend(context, audioFile, sosEntity, localId, dao, sosId = null)
    }

    // ─────────────────────────────────────────────────────────
    // Bridgefy mesh fallback
    // ─────────────────────────────────────────────────────────
    private fun sendViaMesh(sosEntity: SosEventEntity) {
        val packet = SOSPacket(
            userUUID      = sosEntity.victimId,
            lat           = sosEntity.lat,
            lng           = sosEntity.lng,
            timestamp     = sosEntity.timestamp,
            emergencyType = sosEntity.triggerMethod,
            hopCount      = 0
        )
        Log.i(TAG, "📡 Bridgefy mesh send: $packet")
        BridgefyMesh.sendSos(packet)
    }

    // ─────────────────────────────────────────────────────────
    // Incoming mesh packet relay (called by BridgefyMesh)
    // ─────────────────────────────────────────────────────────
    suspend fun handleIncomingMeshPacket(packet: SOSPacket, context: Context) {
        Log.i(TAG, "📥 Incoming mesh SOS from ${packet.userUUID}: lat=${packet.lat} lng=${packet.lng} type=${packet.emergencyType}")

        val db  = AppDatabase.getInstance(context)
        val dao = db.sosEventDao()

        val sosEntity = SosEventEntity(
            victimId      = packet.userUUID,
            lat           = packet.lat,
            lng           = packet.lng,
            triggerMethod = packet.emergencyType,
            riskScore     = 0,
            batteryLevel  = getBatteryLevel(context),
            timestamp     = packet.timestamp,
            uploaded      = false,
            audioFilePath = null
        )
        val id = dao.insert(sosEntity)
        Log.i(TAG, "💾 Relayed mesh SosEvent persisted locally id=$id")

        val online = ConnectivityHelper.isOnline(context)
        Log.i(TAG, "🌐 Relay connectivity check: online=$online")

        if (online) {
            Log.i(TAG, "☁️ Relaying to backend via /api/sos/relay")
            scope.launch {
                postJsonToBackend(sosEntity, id, dao, useRelay = true)
            }
        } else {
            Log.i(TAG, "📡 No internet — mesh SOS left in Room (id=$id) for WorkManager retry")
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level <= 0) 50 else level
        } catch (e: Exception) { 50 }
    }
}


/* ── END OF FILE ─────────────────────────────────────────── */
