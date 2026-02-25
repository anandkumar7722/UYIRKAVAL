package com.hacksrm.nirbhay

import android.util.Log

object MeshSosSender {

    private const val HARDCODED_UUID = "e42a09d6-0336-5c78-9bfa-be7757f1d242"

    fun sendSos(risk: Int) {
        val coords = LocationHelper.getLatLng()

        if (coords == null) {
            Log.w("MeshSosSender", "Location not available yet, sending with 0.0 coords")
        }

        val packet = SOSPacket(
            userUUID = HARDCODED_UUID,
            lat = coords?.first ?: 0.0,
            lng = coords?.second ?: 0.0,
            timestamp = System.currentTimeMillis(),
            emergencyType = "SOS_RISK_$risk",
            hopCount = 0
        )
        // Debug: log current mesh status and send a plain-text test message
        Log.d("MeshSosSender", "Bridgefy started=${BridgefyMesh.isStarted()} currentUser=${BridgefyMesh.currentUserIdStr()}")
        BridgefyMesh.sendSos(packet)
        BridgefyMesh.sendTestMessage("TEST_SOS from ${HARDCODED_UUID.take(8)} risk=$risk ts=${packet.timestamp}")
    }
}