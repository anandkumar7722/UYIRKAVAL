package com.hacksrm.nirbhay

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.hacksrm.nirbhay.sos.SOSEngine
import me.bridgefy.Bridgefy
import me.bridgefy.commons.TransmissionMode
import me.bridgefy.commons.exception.BridgefyException
import me.bridgefy.commons.listener.BridgefyDelegate
import me.bridgefy.logger.enums.LogType
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SOSPacket(
    val userUUID: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val emergencyType: String,
    val hopCount: Int
) {
    fun toByteArray(): ByteArray {
        val json = JSONObject().apply {
            put("userUUID", userUUID)
            put("lat", lat)
            put("lng", lng)
            put("timestamp", timestamp)
            put("emergencyType", emergencyType)
            put("hopCount", hopCount)
        }
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromByteArray(data: ByteArray): SOSPacket? {
            return try {
                val json = JSONObject(String(data, StandardCharsets.UTF_8))
                SOSPacket(
                    userUUID = json.getString("userUUID"),
                    lat = json.getDouble("lat"),
                    lng = json.getDouble("lng"),
                    timestamp = json.getLong("timestamp"),
                    emergencyType = json.getString("emergencyType"),
                    hopCount = json.getInt("hopCount")
                )
            } catch (e: Exception) {
                Log.e("SOSPacket", "Failed to parse packet", e)
                null
            }
        }
    }
}

object BridgefyMesh {
    private var bridgefy: Bridgefy? = null
    private var appContext: Context? = null
    private var lastStartedUser: UUID? = null

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages = _messages.asStateFlow()

    // Expose parsed packets too, for future use
    private val _packets = MutableStateFlow<List<SOSPacket>>(emptyList())
    val packets = _packets.asStateFlow()

    fun init(context: Context, apiKey: UUID) {
        if (bridgefy != null) return
        appContext = context.applicationContext
        val instance = Bridgefy(appContext!!)
        bridgefy = instance
        try {
            instance.init(apiKey, delegate = Delegate, logging = LogType.ConsoleLogger(Log.WARN))
            Log.d("BridgefyMesh", "init() successful")
        } catch (e: BridgefyException) {
            Log.e("BridgefyMesh", "init failed", e)
        }
    }

    /**
     * Start Bridgefy. Safe to call multiple times — guarded against double-start.
     * Called by MainActivity after runtime permissions are granted.
     */
    fun start(userId: UUID? = null) {
        val instance = bridgefy ?: run {
            Log.w("BridgefyMesh", "start() called before init() — ignoring")
            return
        }

        // Guard: Bridgefy SDK fires onStarted internally in some versions right after init().
        // If it's already running, skip to avoid SessionErrorException.
        if (instance.isStarted) {
            Log.d("BridgefyMesh", "start() skipped — already running as ${instance.currentUserId().getOrNull()}")
            return
        }

        try {
            if (userId != null) {
                instance.start(userId = userId)
                lastStartedUser = userId
                Log.d("BridgefyMesh", "start() → userId=$userId")
            } else {
                instance.start()
                Log.d("BridgefyMesh", "start() → Bridgefy will assign userId")
            }
        } catch (e: Exception) {
            // Catch "already started" race condition (can happen on fast devices)
            Log.w("BridgefyMesh", "start() exception (likely already started): ${e.message}")
        }
    }

    fun sendSos(packet: SOSPacket) {
        val instance = bridgefy
        if (instance == null || !instance.isStarted) {
            Log.w("BridgefyMesh", "Cannot send — Bridgefy not started")
            return
        }
        val sender = instance.currentUserId().getOrNull() ?: UUID.randomUUID()
        try {
            instance.send(packet.toByteArray(), TransmissionMode.Broadcast(sender))
            Log.d("BridgefyMesh", "Sent SOSPacket: $packet")
        } catch (e: Exception) {
            Log.e("BridgefyMesh", "Send failed", e)
        }
    }

    // Helpful debug accessors
    fun isStarted(): Boolean = bridgefy?.isStarted ?: false
    fun currentUserIdStr(): String? = bridgefy?.currentUserId()?.getOrNull()?.toString() ?: lastStartedUser?.toString()

    /**
     * Send a plain-text test message (not SOSPacket) over Bridgefy so you can validate
     * peer receive behavior during debugging.
     */
    fun sendTestMessage(text: String) {
        val instance = bridgefy
        if (instance == null || !instance.isStarted) {
            Log.w("BridgefyMesh", "Cannot send test — Bridgefy not started")
            return
        }
        try {
            instance.send(text.toByteArray(StandardCharsets.UTF_8), TransmissionMode.Broadcast(UUID.randomUUID()))
            Log.d("BridgefyMesh", "Sent test message: $text")
        } catch (e: Exception) {
            Log.e("BridgefyMesh", "Send test failed", e)
        }
    }

    private object Delegate : BridgefyDelegate {
        override fun onReceiveData(data: ByteArray, messageID: UUID, transmissionMode: TransmissionMode) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val packet = SOSPacket.fromByteArray(data)
            if (packet != null) {
                // Store parsed packet
                _packets.update { it + packet }

                // Human-readable display message
                val displayMessage = "[$time] SOS from ${packet.userUUID.take(8)}... | " +
                        "Type: ${packet.emergencyType} | " +
                        "Lat: ${packet.lat}, Lng: ${packet.lng} | " +
                        "Hops: ${packet.hopCount}"

                Log.d("BridgefyMesh", "Received SOSPacket: $packet")
                Log.d("BridgefyMesh", "Delegate: bridgefy.isStarted=${bridgefy?.isStarted} currentUser=${bridgefy?.currentUserId()?.getOrNull()} appContext=${appContext != null}")
                _messages.update { it + displayMessage }

                // Let SOSEngine handle incoming mesh packet: persist locally and attempt relay
                try {
                    val ctx = appContext
                    if (ctx != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                SOSEngine.handleIncomingMeshPacket(packet, ctx)
                            } catch (t: Throwable) {
                                Log.w("BridgefyMesh", "SOSEngine.handleIncomingMeshPacket failed: ${t.message}")
                            }
                        }
                    } else {
                        Log.w("BridgefyMesh", "No app context available to forward mesh packet to SOSEngine")
                    }
                } catch (t: Throwable) {
                    Log.w("BridgefyMesh", "Couldn't forward packet to SOSEngine: ${t.message}")
                }

            } else {
                // Fallback for non-SOS messages
                val text = String(data, StandardCharsets.UTF_8)
                val displayMessage = "[$time] $text"
                Log.d("BridgefyMesh", "Received raw: $displayMessage")
                _messages.update { it + displayMessage }
            }
        }

        override fun onStarted(userId: UUID) {
            Log.d("BridgefyMesh", "onStarted: $userId")
            lastStartedUser = userId
        }
        override fun onStopped() { Log.d("BridgefyMesh", "onStopped") }
        override fun onFailToStart(error: BridgefyException) { Log.e("BridgefyMesh", "FailStart", error) }
        override fun onConnected(peerID: UUID) { Log.d("BridgefyMesh", "Connected: $peerID") }
        override fun onEstablishSecureConnection(peerID: UUID) { Log.d("BridgefyMesh", "Secure session: $peerID") }
        override fun onFailToEstablishSecureConnection(peerID: UUID, error: BridgefyException) { Log.e("BridgefyMesh", "Secure session failed: $peerID", error) }
        override fun onFailToStop(error: BridgefyException) {}
        override fun onDestroySession() {}
        override fun onFailToDestroySession(error: BridgefyException) {}
        override fun onDisconnected(peerID: UUID) {}
        override fun onConnectedPeers(connectedPeers: List<UUID>) {}
        override fun onSend(messageID: UUID) { Log.d("BridgefyMesh", "onSend: messageID=$messageID") }
        override fun onProgressOfSend(messageID: UUID, progress: Int, totalSize: Int) { Log.d("BridgefyMesh", "onProgressOfSend: $messageID $progress/$totalSize") }
        override fun onFailToSend(messageID: UUID, error: BridgefyException) { Log.e("BridgefyMesh", "onFailToSend: $messageID", error) }
    }
}