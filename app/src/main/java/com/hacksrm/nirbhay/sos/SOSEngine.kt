package com.hacksrm.nirbhay.sos

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hacksrm.nirbhay.SOSPacket
import com.hacksrm.nirbhay.BridgefyMesh
import com.hacksrm.nirbhay.LocationHelper
import com.hacksrm.nirbhay.auth.AuthRepository
import com.hacksrm.nirbhay.connectivity.ConnectivityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private const val FALLBACK_USER_UUID = "e42a09d6-0336-5c78-9bfa-be7757f1d242"

    /** Returns the logged-in user_id from SharedPreferences, or the fallback hardcoded one. */
    fun getUserId(context: Context): String =
        AuthRepository.getUserId(context) ?: FALLBACK_USER_UUID

    private val mutex = Mutex()
    private var lastTriggerMs = 0L
    private const val COOLDOWN_MS = 10_000L
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Audio recording state observable by UI ───────────────
    enum class AudioState { IDLE, RECORDING, SAVED, UPLOADED, FAILED }

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    /** Observe this from Compose to show recording / saved / uploaded status */
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    // ── Last trigger source observable by UI ─────────────────
    private val _lastTriggerSource = MutableStateFlow(TriggerSource.BUTTON)
    /** Observe this to display the real trigger reason in the SOS screen */
    val lastTriggerSource: StateFlow<TriggerSource> = _lastTriggerSource.asStateFlow()

    // ── Retrofit instance (shared, lazy) ─────────────────────
    private val retrofitApi: SosApi by lazy {
        val logging = HttpLoggingInterceptor { msg -> Log.i("OkHttp-SOS", msg) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://nirbhay-5gcekoejfa-el.a.run.app")
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
        _audioState.value = AudioState.IDLE
        _lastTriggerSource.value = source

        // Get GPS
        val (lat, lng) = LocationHelper.getLatLng() ?: Pair(0.0, 0.0)
        Log.i(TAG, "📍 Location: lat=$lat, lng=$lng")

        // Get logged-in user_id
        val userId = getUserId(context)
        Log.i(TAG, "👤 victim_id: $userId")

        // ── STEP 1: Persist to Room ──────────────────────────
        val db  = AppDatabase.getInstance(context)
        val dao = db.sosEventDao()
        val triggerMethod = source.name.lowercase()
        val batteryLevel  = getBatteryLevel(context)
        val riskScore     = RiskScoreEngine.currentScore()

        val sosEntity = SosEventEntity(
            victimId      = userId,
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

        // ── STEP 3 & 4: Photo capture + Audio recording in parallel,
        //    then combined upload via /api/sos/media ──────────────
        val capturedSosId = sosId
        scope.launch {
            // --- Photo capture (async) ---
            val photosDeferred = async {
                Log.i(TAG, "📸 ─── STARTING PHOTO CAPTURE ───")
                try {
                    val photoCapture = SosPhotoCapture(context)
                    val photos = photoCapture.captureAll()
                    Log.i(TAG, "📸 Photo capture finished: ${photos.size} photos saved locally")
                    photos.forEach { f ->
                        Log.i(TAG, "📸   ${f.name}  (${f.length() / 1024} KB) → ${f.absolutePath}")
                    }
                    photos
                } catch (e: Exception) {
                    Log.e(TAG, "📸 Photo capture failed: ${e.message}", e)
                    emptyList()
                }
            }

            // --- Audio recording (sequential, needs mic exclusivity) ---
            Log.i(TAG, "🎙️ ─── AUDIO RECORDING START ───")
            _audioState.value = AudioState.RECORDING
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
            delay(600)

            // Start recording
            val recorder = AudioRecorder()
            recorder.start(context, now)
            Log.i(TAG, "🎙️ AudioRecorder.start() called — recording 60 s to local storage")
            Log.i(TAG, "🎙️ File will be at: ${recorder.currentPath()}")

            // Wait for the full 60-second recording
            delay(61_000)

            // Stop and get path
            val audioPath = recorder.stop()
            Log.i(TAG, "🎙️ AudioRecorder.stop() called — result path: $audioPath")

            val audioFile = if (audioPath != null) {
                val f = File(audioPath)
                Log.i(TAG, "🎙️ Audio file saved locally: $audioPath  (${f.length()} bytes)")
                _audioState.value = AudioState.SAVED

                // Update Room with audio path
                val updated = sosEntity.copy(id = localId, audioFilePath = audioPath)
                dao.update(updated)
                Log.i(TAG, "💾 Room updated: SosEvent id=$localId now has audioFilePath=$audioPath")
                f
            } else {
                Log.w(TAG, "🎙️ Recording failed or produced empty file")
                _audioState.value = AudioState.FAILED
                null
            }

            // --- Wait for photos to finish ---
            val photos = photosDeferred.await()
            Log.i(TAG, "📸 ─── PHOTO CAPTURE DONE: ${photos.size} photos ready ───")

            // --- Upload everything via /api/sos/media if online ---
            val onlineNow = ConnectivityHelper.isOnline(context)
            Log.i(TAG, "🌐 Post-recording connectivity check: online=$onlineNow")

            if (onlineNow && (audioFile != null || photos.isNotEmpty())) {
                Log.i(TAG, "☁️ Uploading media (${photos.size} photos + ${if (audioFile != null) "audio" else "no audio"}) to backend…")
                uploadMediaToBackend(
                    context   = context,
                    audioFile = audioFile,
                    photos    = photos,
                    sosEntity = sosEntity.copy(id = localId, audioFilePath = audioPath),
                    localId   = localId,
                    dao       = dao,
                    sosId     = capturedSosId
                )
            } else if (!onlineNow) {
                Log.i(TAG, "📡 No internet after recording — media will be uploaded by WorkManager when connectivity returns")
            } else {
                Log.w(TAG, "⚠️ No audio and no photos to upload")
            }

            // Restart ScreamDetector
            try {
                val startIntent = Intent(context, com.hacksrm.nirbhay.ScreamDetectionService::class.java)
                context.startForegroundService(startIntent)
                Log.i(TAG, "🎙️ ScreamDetectionService restarted after recording")
            } catch (t: Throwable) {
                Log.w(TAG, "🎙️ Could not restart ScreamDetectionService: ${t.message}")
            }

            Log.i(TAG, "🎙️ ─── MEDIA CAPTURE & UPLOAD COMPLETE ───")
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
                lat             = sosEntity.lat,
                lng             = sosEntity.lng,
                trigger_method  = sosEntity.triggerMethod,
                risk_score      = sosEntity.riskScore,
                battery_level   = sosEntity.batteryLevel
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
    // Upload audio + photos via multipart to POST /api/sos/media
    // Sends: sos_id, victim_id, images (up to 10), audio (optional)
    // ─────────────────────────────────────────────────────────
    private suspend fun uploadMediaToBackend(
        context   : Context,
        audioFile : File?,
        photos    : List<File>,
        sosEntity : SosEventEntity,
        localId   : Long,
        dao       : SosEventDao,
        sosId     : String?
    ) {
        try {
            val mediaSosId = sosId ?: java.util.UUID.randomUUID().toString()

            Log.i(TAG, "📤 ─── MEDIA UPLOAD START ───")
            Log.i(TAG, "📤 Endpoint: POST /api/sos/media (multipart)")
            Log.i(TAG, "📤 sos_id:    $mediaSosId")
            Log.i(TAG, "📤 victim_id: ${sosEntity.victimId}")
            Log.i(TAG, "📤 Audio:     ${audioFile?.absolutePath ?: "null (not sending)"}")
            Log.i(TAG, "📤 Photos:    ${photos.size} file(s)")

            fun strPart(value: String) =
                value.toRequestBody("text/plain".toMediaTypeOrNull())

            // ── Build image parts (field name = "images" for each, up to 10) ──
            val imageParts = photos
                .filter { it.exists() && it.length() > 0 }
                .take(10)
                .mapIndexed { i, file ->
                    Log.i(TAG, "📤   image[$i]: ${file.name} (${file.length() / 1024} KB)")
                    val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("images", file.name, body)
                }

            // ── Build audio part (optional) ──
            val audioPart = if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                Log.i(TAG, "📤   audio: ${audioFile.name} (${audioFile.length() / 1024} KB)")
                val body = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("audio", audioFile.name, body)
            } else {
                Log.i(TAG, "📤   audio: null — not attached")
                null
            }

            Log.i(TAG, "📤 Sending multipart POST /api/sos/media (${imageParts.size} images, audio=${audioPart != null}) …")
            val resp = retrofitApi.uploadMedia(
                sosId    = strPart(mediaSosId),
                victimId = strPart(sosEntity.victimId),
                images   = imageParts,
                audio    = audioPart
            )

            if (resp.isSuccessful) {
                val body = resp.body()
                Log.i(TAG, "✅ ─── MEDIA UPLOAD SUCCESS ───")
                Log.i(TAG, "✅ sos_id=${body?.sos_id}")
                Log.i(TAG, "✅ audio_url=${body?.audio_url ?: "none"}")
                Log.i(TAG, "✅ image_urls=${body?.image_urls?.joinToString() ?: "none"}")
                Log.i(TAG, "✅ ${imageParts.size} photos + ${if (audioPart != null) "audio" else "no audio"} delivered to /api/sos/media")
                dao.markUploaded(localId)
                _audioState.value = AudioState.UPLOADED
            } else {
                val errBody = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                Log.w(TAG, "❌ ─── MEDIA UPLOAD FAILED ───")
                Log.w(TAG, "❌ HTTP ${resp.code()} — $errBody")
                Log.w(TAG, "❌ Media remains in Room (id=$localId) for WorkManager retry")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Media upload exception: ${e.message}", e)
            Log.w(TAG, "❌ Media remains in Room (id=$localId) for WorkManager retry")
        }
        Log.i(TAG, "📤 ─── MEDIA UPLOAD DONE ───")
    }

    // ─────────────────────────────────────────────────────────
    // PUBLIC: expose uploadMediaToBackend for UploadQueueWorker
    // ─────────────────────────────────────────────────────────
    suspend fun uploadMediaForEvent(
        context   : Context,
        audioFile : File?,
        photos    : List<File>,
        sosEntity : SosEventEntity,
        localId   : Long,
        dao       : SosEventDao
    ) {
        uploadMediaToBackend(context, audioFile, photos, sosEntity, localId, dao, sosId = null)
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
