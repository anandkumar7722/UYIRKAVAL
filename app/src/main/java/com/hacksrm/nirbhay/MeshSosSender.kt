package com.hacksrm.nirbhay

object MeshSosSender {

    fun sendSos(lat: Double?, lon: Double?, risk: Int) {
        // Temporary: for test we send a broadcast SOS message. We'll later encode lat/lon too.
        BridgefyMesh.sendSosTest(risk = risk)
    }
}

