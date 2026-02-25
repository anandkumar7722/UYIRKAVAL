package com.hacksrm.nirbhay

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages = _messages.asStateFlow()

    // Expose parsed packets too, for future use
    private val _packets = MutableStateFlow<List<SOSPacket>>(emptyList())
    val packets = _packets.asStateFlow()

    fun init(context: Context, apiKey: UUID) {
        if (bridgefy != null) return
        val instance = Bridgefy(context.applicationContext)
        bridgefy = instance
        try {
            instance.init(apiKey, delegate = Delegate, logging = LogType.ConsoleLogger(Log.WARN))
            Log.d("BridgefyMesh", "init() successful")
        } catch (e: BridgefyException) {
            Log.e("BridgefyMesh", "init failed", e)
        }
    }

    fun start(userId: UUID? = null) {
        val instance = bridgefy ?: return
        try {
            instance.start(userId = userId)
            Log.d("BridgefyMesh", "start() called")
        } catch (e: Exception) {
            Log.e("BridgefyMesh", "start failed", e)
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
                _messages.update { it + displayMessage }
            } else {
                // Fallback for non-SOS messages
                val text = String(data, StandardCharsets.UTF_8)
                val displayMessage = "[$time] $text"
                Log.d("BridgefyMesh", "Received raw: $displayMessage")
                _messages.update { it + displayMessage }
            }
        }

        override fun onStarted(userId: UUID) { Log.d("BridgefyMesh", "onStarted: $userId") }
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
        override fun onSend(messageID: UUID) {}
        override fun onProgressOfSend(messageID: UUID, progress: Int, totalSize: Int) {}
        override fun onFailToSend(messageID: UUID, error: BridgefyException) {}
    }
}