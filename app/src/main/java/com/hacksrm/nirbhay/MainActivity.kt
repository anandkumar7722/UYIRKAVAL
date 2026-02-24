package com.hacksrm.nirbhay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
                    HomeScreen(modifier = Modifier.padding(innerPadding))
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

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val messageList by BridgefyMesh.messages.collectAsState()

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Nirbhay – Mesh Status",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                Log.d("HomeScreen", "Sending SOS...")
                MeshSosSender.sendSos(risk = 80)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Send Test Mesh SOS")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Incoming Messages (${messageList.size}):",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (messageList.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.LightGray.copy(alpha = 0.1f))
            ) {
                items(messageList.reversed()) { message ->
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Waiting for messages... (Check if Bluetooth is ON)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    NirbhayTheme {
        HomeScreen()
    }
}