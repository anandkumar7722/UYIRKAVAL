package com.hacksrm.nirbhay.Mesh

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import android.os.Bundle
import com.hacksrm.nirbhay.BridgefyMesh
import com.hacksrm.nirbhay.LocationHelper
import java.util.UUID

class NirbhayApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Read API key from AndroidManifest <meta-data android:name="com.bridgefy.sdk.API_KEY" ... />
        val apiKey = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val meta: Bundle? = appInfo.metaData
            val raw = meta?.getString("com.bridgefy.sdk.API_KEY")?.trim()
            UUID.fromString(raw)
        }.getOrNull()

        if (apiKey == null) {
            Log.e("NirbhayApp", "Bridgefy API key missing/invalid. Add meta-data com.bridgefy.sdk.API_KEY")
            return
        }

        // Initialize Bridgefy once; actual start happens after permissions in MainActivity
        BridgefyMesh.init(this, apiKey)
        LocationHelper.init(this)
    }
}

