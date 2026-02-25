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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Central SOS engine.
 * Flow:
 *  1. Save event to Room immediately
 *  2. Stop ScreamDetector (so mic is free for AudioRecorder)
 *  3. Start AudioRecorder (60s)
 *  4. If ONLINE → POST to /api/sos/trigger
 *  5. If OFFLINE (or API fails) → send via Bridgefy mesh
 */
object SOSEngine {
    private const val TAG = "SOSEngine"
    const val HARDCODED_USER_UUID = "00000000-0000-0000-0000-000000000001"
    const val HARDCODED_EMERGENCY_TOKEN = "tok_demo_123456"

    private val mutex = Mutex()
    private var lastTriggerMs = 0L
    private const val COOLDOWN_MS = 10_000L
    private val scope = CoroutineScope(Dispatchers.IO)

    // Retrofit API for the real backend
    private val retrofitApi: SosApi by lazy {
        val client = okhttp3.OkHttpClient.Builder().apply {
            val logging = okhttp3.logging.HttpLoggingInterceptor()
            logging.level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            addInterceptor(logging)
        }.build()

        Retrofit.Builder()
            .baseUrl("https://nirbhay-467822196904.asia-south1.run.app/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SosApi::class.java)
    }

    suspend fun triggerSOS(source: TriggerSource, context: Context) {
        val now = System.currentTimeMillis()
        mutex.withLock {
            if (now - lastTriggerMs < COOLDOWN_MS) {
                Log.d(TAG, "In cooldown, ignoring trigger from $source")
                return
            }
            lastTriggerMs = now
        }

        Log.d(TAG, "Starting SOS trigger from $source")

        // Get location (best-effort)
        val (lat, lng) = LocationHelper.getLatLng() ?: Pair(0.0, 0.0)

        // 1. Save to Room immediately so event is never lost
        val db = AppDatabase.getInstance(context)
        val dao = db.sosEventDao()
        val triggerMethod = source.name.lowercase()
        val sosEntity = SosEventEntity(
            victimId = HARDCODED_USER_UUID,
            lat = lat,
            lng = lng,
            triggerMethod = triggerMethod,
            riskScore = RiskScoreEngine.currentScore(),
            batteryLevel = getBatteryLevel(context),
            timestamp = now,
            uploaded = false,
            audioFilePath = null
        )
        val id = dao.insert(sosEntity)
        Log.d(TAG, "SosEvent persisted locally id=$id")

        // 2. Stop ScreamDetector so mic is free for MediaRecorder (they can't both hold the mic)
        try {
            val stopIntent = Intent(context, com.hacksrm.nirbhay.ScreamDetectionService::class.java)
            context.stopService(stopIntent)
            Log.d(TAG, "ScreamDetectionService stopped to free mic for AudioRecorder")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not stop ScreamDetectionService: ${t.message}")
        }

        // 3. Start audio recording in background (60s)
        scope.launch {
            try {
                // Small delay to let the OS release the mic from AudioRecord
                kotlinx.coroutines.delay(500)
                val recorder = AudioRecorder()
                recorder.start(context, now)
                // Wait for 61s, then stop (AudioRecorder auto-stops at 60s but we ensure it's done)
                kotlinx.coroutines.delay(61_000)
                val path = recorder.stop()
                if (path != null) {
                    val updated = sosEntity.copy(id = id, audioFilePath = path)
                    dao.update(updated)
                    Log.d(TAG, "SosEvent id=$id updated with audio=$path")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio recording failed: ${e.message}")
            }

            // Restart ScreamDetector after recording is done
            try {
                val startIntent = Intent(context, com.hacksrm.nirbhay.ScreamDetectionService::class.java)
                context.startForegroundService(startIntent)
                Log.d(TAG, "ScreamDetectionService restarted after recording")
            } catch (t: Throwable) {
                Log.w(TAG, "Could not restart ScreamDetectionService: ${t.message}")
            }
        }

        // 4. Check connectivity and choose online or offline path
        val online = ConnectivityHelper.isOnline(context)
        Log.d(TAG, "Connectivity check: online=$online, lat=$lat, lng=$lng, source=$source")

        if (online) {
            Log.d(TAG, "Device is ONLINE — posting SOS to backend via /api/sos/trigger")
            scope.launch {
                postToBackend(context, sosEntity, id, dao, useRelay = false)
            }
        } else {
            Log.d(TAG, "Device is OFFLINE — sending SOS via Bridgefy mesh")
            sendViaMesh(sosEntity)
        }
    }

    private suspend fun postToBackend(
        context: Context,
        sosEntity: SosEventEntity,
        id: Long,
        dao: SosEventDao,
        useRelay: Boolean
    ) {
        try {
            val req = SosCreateRequest(
                victim_id = sosEntity.victimId,
                emergency_token = HARDCODED_EMERGENCY_TOKEN,
                lat = sosEntity.lat,
                lng = sosEntity.lng,
                trigger_method = sosEntity.triggerMethod,
                risk_score = sosEntity.riskScore,
                battery_level = sosEntity.batteryLevel,
                timestamp = sosEntity.timestamp,
                audio_file_path = null
            )

            val endpoint = if (useRelay) "/api/sos/relay" else "/api/sos/trigger"
            Log.d(TAG, "→ HTTP POST $endpoint payload: victimId=${req.victim_id} lat=${req.lat} lng=${req.lng} method=${req.trigger_method} risk=${req.risk_score} battery=${req.battery_level}")

            val resp = if (useRelay) {
                retrofitApi.relay(req)
            } else {
                retrofitApi.trigger(req)
            }

            if (resp.isSuccessful) {
                Log.d(TAG, "✅ SOS posted to backend successfully; sos_id=${resp.body()?.sos_id} is_new=${resp.body()?.is_new}")
                dao.markUploaded(id)
            } else {
                val errBody = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                Log.w(TAG, "❌ API failure code=${resp.code()} err=$errBody — falling back to mesh")
                sendViaMesh(sosEntity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network request failed: ${e.message} — falling back to mesh")
            sendViaMesh(sosEntity)
        }
    }

    private fun sendViaMesh(sosEntity: SosEventEntity) {
        val packet = SOSPacket(
            userUUID = sosEntity.victimId,
            lat = sosEntity.lat,
            lng = sosEntity.lng,
            timestamp = sosEntity.timestamp,
            emergencyType = sosEntity.triggerMethod,
            hopCount = 0
        )
        Log.d(TAG, "📡 Sending via Bridgefy mesh: $packet")
        BridgefyMesh.sendSos(packet)
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level <= 0) 50 else level
        } catch (e: Exception) {
            50
        }
    }

    /**
     * Called by BridgefyMesh when it receives an SOS packet from another device.
     * If online → POST to /api/sos/relay; if offline → skip (leave for WorkManager retry).
     */
    suspend fun handleIncomingMeshPacket(packet: SOSPacket, context: Context) {
        Log.d(TAG, "📥 Received mesh SOS packet from ${packet.userUUID}: lat=${packet.lat} lng=${packet.lng} type=${packet.emergencyType}")

        val db = AppDatabase.getInstance(context)
        val dao = db.sosEventDao()

        val sosEntity = SosEventEntity(
            victimId = packet.userUUID,
            lat = packet.lat,
            lng = packet.lng,
            triggerMethod = packet.emergencyType,
            riskScore = 0,
            batteryLevel = getBatteryLevel(context),
            timestamp = packet.timestamp,
            uploaded = false,
            audioFilePath = null
        )
        val id = dao.insert(sosEntity)
        Log.d(TAG, "Incoming mesh SosEvent persisted locally id=$id")

        val online = ConnectivityHelper.isOnline(context)
        if (online) {
            Log.d(TAG, "📡→☁️ Relaying received mesh SOS to backend via /api/sos/relay")
            scope.launch {
                postToBackend(context, sosEntity, id, dao, useRelay = true)
            }
        } else {
            Log.d(TAG, "📡 No internet to relay mesh SOS — leaving in Room for WorkManager retry (id=$id)")
        }
    }
}


/* ── END OF FILE ─────────────────────────────────────────── */
