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
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object BridgefyMesh {
    private var bridgefy: Bridgefy? = null

    // Store a list of messages instead of just the last one
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages = _messages.asStateFlow()

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
            Log.e("BridgefyMesh", "start failed, retrying in 2s", e)
            // Retry once after delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { instance.start(userId = userId) } catch (e2: Exception) {
                    Log.e("BridgefyMesh", "Retry also failed", e2)
                }
            }, 2000)
        }
    }

    fun sendSosTest(risk: Int = 80) {
        val instance = bridgefy
        if (instance == null || !instance.isStarted) return
        val sender = instance.currentUserId().getOrNull() ?: UUID.randomUUID()
        val payload = "SOS: Risk $risk at ${System.currentTimeMillis()}".toByteArray(StandardCharsets.UTF_8)
        try {
            instance.send(payload, TransmissionMode.Broadcast(sender))
            Log.d("BridgefyMesh", "Sent SOS Broadcast")
        } catch (e: Exception) {
            Log.e("BridgefyMesh", "Send failed", e)
        }
    }

    private object Delegate : BridgefyDelegate {
        override fun onReceiveData(data: ByteArray, messageID: UUID, transmissionMode: TransmissionMode) {
            val text = String(data, StandardCharsets.UTF_8)
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val displayMessage = "[$time] $text"
            
            Log.d("BridgefyMesh", "Received: $displayMessage")
            
            // Add to list - this will always trigger a UI update
            _messages.update { currentList -> currentList + displayMessage }
        }

        override fun onStarted(userId: UUID) {
            Log.d("BridgefyMesh", "onStarted: $userId")
        }
        
        override fun onStopped() {
            Log.d("BridgefyMesh", "onStopped")
        }
        
        override fun onFailToStart(error: BridgefyException) {
            Log.e("BridgefyMesh", "FailStart", error)
        }
        
        override fun onConnected(peerID: UUID) {
            Log.d("BridgefyMesh", "Connected: $peerID")
        }
        
        // Boilerplate overrides with matching parameter names and Unit return types
        override fun onFailToStop(error: BridgefyException) {}
        override fun onDestroySession() {}
        override fun onFailToDestroySession(error: BridgefyException) {}
        override fun onDisconnected(peerID: UUID) {}
        override fun onConnectedPeers(connectedPeers: List<UUID>) {}
        override fun onEstablishSecureConnection(peerID: UUID) {}
        override fun onFailToEstablishSecureConnection(peerID: UUID, error: BridgefyException) {}
        override fun onSend(messageID: UUID) {}
        override fun onProgressOfSend(messageID: UUID, progress: Int, totalSize: Int) {}
        override fun onFailToSend(messageID: UUID, error: BridgefyException) {}
    }
}
