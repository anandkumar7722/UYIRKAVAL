package com.hacksrm.nirbhay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hacksrm.nirbhay.ui.theme.NirbhayTheme

/**
 * ============================================================
 * MainActivity  –  Entry Point & SOS Broadcast Hub
 * ============================================================
 *
 * Responsibilities:
 *  1. Request ALL required permissions (Bluetooth, Location,
 *     Microphone, Notifications) before anything starts.
 *  2. Start both Edge-AI foreground services:
 *     - ScreamDetectionService (YAMNet TFLite)
 *     - FallDetectionService   (Accelerometer)
 *  3. Start Bridgefy mesh + location tracking.
 *  4. Register a LocalBroadcastReceiver for ACTION_SOS_TRIGGERED.
 *     When the receiver fires, it flips a Compose state flag
 *     that causes NirbhayNav to navigate to the SOS countdown.
 * ============================================================
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // ── Observable trigger reason for Compose ────────────────
    // When non-null, NirbhayNav will navigate to the SOS screen.
    val sosTriggerReason = mutableStateOf<String?>(null)

    // ── Permissions ──────────────────────────────────────────
    // We request ALL permissions in a single batch so the user
    // sees one dialog, not three.
    private val allPermissions: Array<String>
        get() = buildList {
            // Bluetooth mesh
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            // Microphone (for ScreamDetectionService)
            add(Manifest.permission.RECORD_AUDIO)
            // Camera (for silent SOS photo capture)
            add(Manifest.permission.CAMERA)
            // Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.all { it.value }
            val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (allGranted) {
                Log.d(TAG, "✅ All permissions granted")
            } else {
                Log.w(TAG, "⚠️ Some permissions denied: " +
                        results.filter { !it.value }.keys.joinToString())
            }

            // Start mesh only if location + BT were just granted via launcher
            // (if already granted we called startMesh() directly in onCreate — avoid double start)
            if (locationGranted && !BridgefyMesh.isStarted()) {
                startMesh()
            }

            // Start Edge-AI services if mic was granted
            if (audioGranted) {
                startEdgeAiServices()
            } else {
                Log.e(TAG, "❌ RECORD_AUDIO denied – ScreamDetectionService will NOT start. " +
                        "The scream detection feature is disabled.")
            }
        }

    // ── Broadcast Receiver: catches SOS from both services ──
    private val sosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val reason = intent?.getStringExtra(ScreamDetectionService.EXTRA_REASON) ?: "UNKNOWN"
            Log.e(TAG, "🚨🚨🚨 SOS BROADCAST RECEIVED in MainActivity: reason=$reason")

            when (reason) {
                "SCREAM_DETECTED" -> {
                    val label = intent?.getStringExtra("LABEL") ?: "?"
                    val score = intent?.getFloatExtra("SCORE", 0f) ?: 0f
                    Log.e(TAG, "🎤 Scream: label=$label, score=${(score * 100).toInt()}%")
                }
                "FALL_DETECTED" -> {
                    val gForce = intent?.getFloatExtra("G_FORCE", 0f) ?: 0f
                    val elapsed = intent?.getLongExtra("ELAPSED_MS", 0L) ?: 0L
                    Log.e(TAG, "📱 Fall: gForce=%.2f G, elapsed=${elapsed}ms".format(gForce))
                }
            }

            // Trigger navigation to the SOS countdown screen.
            // NirbhayNav observes this state and navigates automatically.
            sosTriggerReason.value = reason
        }
    }

    // ── Network callback to enqueue UploadQueueWorker when network is back ──
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ── Lifecycle ────────────────────────────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register the SOS broadcast receiver FIRST so we never miss an event
        LocalBroadcastManager.getInstance(this).registerReceiver(
            sosReceiver,
            IntentFilter(ScreamDetectionService.ACTION_SOS_TRIGGERED)
        )
        Log.d(TAG, "✅ SOS BroadcastReceiver registered")

        // Register network callback to trigger upload worker when connectivity returns
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "Network available — enqueueing UploadQueueWorker")
                    val work = OneTimeWorkRequestBuilder<com.hacksrm.nirbhay.sos.UploadQueueWorker>().build()
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "upload_queue_one_shot",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        work
                    )
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register network callback: ${t.message}")
        }

        // Request permissions (or start services immediately if already granted)
        if (hasAllPermissions()) {
            startMesh()
            startEdgeAiServices()
        } else {
            requestPermissionsLauncher.launch(allPermissions)
        }

        setContent {
            NirbhayTheme {
                NirbhayNav(
                    modifier = Modifier.fillMaxSize(),
                    sosTriggerReason = sosTriggerReason,
                )
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sosReceiver)
        Log.d(TAG, "SOS BroadcastReceiver unregistered")

        // Unregister network callback
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to unregister network callback: ${t.message}")
        }

        super.onDestroy()
    }

    // ── Start helpers ────────────────────────────────────────

    private fun startMesh() {
        Log.d(TAG, "Starting Bridgefy mesh + location tracking…")
        BridgefyMesh.start()
        LocationHelper.startTracking()
    }

    /**
     * Start both Edge-AI foreground services.
     * MUST only be called after RECORD_AUDIO is granted.
     */
    private fun startEdgeAiServices() {
        Log.d(TAG, "Starting Edge-AI services…")

        // ── Scream Detection (requires mic) ──
        val screamIntent = Intent(this, ScreamDetectionService::class.java)
        startForegroundService(screamIntent)
        Log.d(TAG, "✅ ScreamDetectionService start requested")

        // ── Fall Detection (no special permission needed) ──
        val fallIntent = Intent(this, FallDetectionService::class.java)
        startForegroundService(fallIntent)
        Log.d(TAG, "✅ FallDetectionService start requested")
    }

    private fun hasAllPermissions(): Boolean {
        return allPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
