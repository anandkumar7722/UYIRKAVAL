package com.hacksrm.nirbhay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
 import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.hacksrm.nirbhay.screens.HomeScreen
import com.hacksrm.nirbhay.ui.theme.NirbhayTheme

class MainActivity : ComponentActivity() {

    private val meshPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.all { it.value }) {
                Log.d("MainActivity", "All permissions granted, starting Bridgefy")
                startMesh()
            } else {
                Log.e("MainActivity", "Some permissions were denied — mesh will not work")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (hasPermissions()) {
            startMesh()
        } else {
            requestPermissionsLauncher.launch(meshPermissions)
        }

        setContent {
            NirbhayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        HomeScreen()
                    }
                }
            }
        }
    }

    private fun startMesh() {
        Log.d("MainActivity", "Starting Bridgefy mesh...")
        BridgefyMesh.start()
        LocationHelper.startTracking()
    }

    private fun hasPermissions(): Boolean {
        return meshPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
