package com.hacksrm.nirbhay.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.hacksrm.nirbhay.FallDetectionService
import com.hacksrm.nirbhay.ScreamDetectionService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═════════════════════════════════════════════════════════════
// Design Tokens — matches the SHE-SHIELD dark theme
// ═════════════════════════════════════════════════════════════
private val BgDark = Color(0xFF110808)
private val BgCard = Color(0xFF1A0D0D)
private val BorderDark = Color(0xFF482323)
private val AccentRed = Color(0xFFEC1313)
private val AccentRedDark = Color(0xFFB00E0E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFFC99292)
private val GreenDot = Color(0xFF22C55E)
private val GreenBg = Color(0x3314532D)
private val GreenBorder = Color(0x6622C55E)
private val AmberWarn = Color(0xFFFBBF24)
private val AmberBorder = Color(0x66FBBF24)
private val AmberBg = Color(0x33534012)
private val CyanInfo = Color(0xFF06B6D4)
private val PurpleService = Color(0xFFA855F7)

/**
 * Timestamp formatter for the event log.
 */
private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/**
 * Single log entry shown in the live event feed.
 */
data class DiagEvent(
    val timestamp: String,
    val reason: String,
    val detail: String,
    val color: Color,
)

// ═════════════════════════════════════════════════════════════
// Root Composable
// ═════════════════════════════════════════════════════════════

@Preview(showSystemUi = true, showBackground = true, backgroundColor = 0xFF110808)
@Composable
fun SystemDiagnosticsScreen() {
    val context = LocalContext.current

    // ── Reactive state ───────────────────────────────────────
    var lastReason by remember { mutableStateOf("IDLE") }
    var flashColor by remember { mutableStateOf(BgDark) }
    var eventLog by remember { mutableStateOf(listOf<DiagEvent>()) }

    // Service running state
    var screamServiceRunning by remember { mutableStateOf(false) }
    var fallServiceRunning by remember { mutableStateOf(false) }

    // Network state
    var hasInternet by remember { mutableStateOf(false) }
    var networkType by remember { mutableStateOf("Unknown") }

    // ── Check network on each recomposition tick ────────────
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        networkType = when {
            caps == null -> "No Connection"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
    }

    // ── Register LocalBroadcast receiver ────────────────────
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val reason = intent?.getStringExtra("REASON") ?: "UNKNOWN"
                val now = timeFormatter.format(Date())

                lastReason = reason

                val (detail, color) = when (reason) {
                    "SCREAM_DETECTED" -> {
                        val label = intent?.getStringExtra("LABEL") ?: "?"
                        val score = intent?.getFloatExtra("SCORE", 0f) ?: 0f
                        val pct = (score * 100).toInt()
                        Pair("Label: $label | Confidence: $pct%", AccentRed)
                    }
                    "FALL_DETECTED" -> {
                        val gForce = intent?.getFloatExtra("G_FORCE", 0f) ?: 0f
                        val elapsed = intent?.getLongExtra("ELAPSED_MS", 0L) ?: 0L
                        Pair("G-Force: %.2f G | Elapsed: ${elapsed}ms".format(gForce), AmberWarn)
                    }
                    else -> Pair("Reason: $reason", CyanInfo)
                }

                flashColor = color

                val event = DiagEvent(
                    timestamp = now,
                    reason = reason,
                    detail = detail,
                    color = color,
                )
                eventLog = listOf(event) + eventLog.take(49) // keep last 50
            }
        }

        val filter = IntentFilter(ScreamDetectionService.ACTION_SOS_TRIGGERED)
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    // ── Animated background flash ───────────────────────────
    val animatedBg by animateColorAsState(
        targetValue = flashColor,
        animationSpec = tween(durationMillis = 600),
        label = "bg-flash",
        finishedListener = { flashColor = BgDark }
    )

    // ── UI ───────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title
        Text(
            text = "⚙ SYSTEM DIAGNOSTICS",
            color = TextWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "SHE-SHIELD Pipeline Test Console",
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        // ── Giant Status Indicator ──────────────────────────
        StatusIndicatorCard(lastReason)

        Spacer(Modifier.height(16.dp))

        // ── Network Status ──────────────────────────────────
        NetworkStatusCard(hasInternet, networkType)

        Spacer(Modifier.height(16.dp))

        // ── Service Controls ────────────────────────────────
        ServiceControlsCard(
            context = context,
            screamRunning = screamServiceRunning,
            fallRunning = fallServiceRunning,
            onScreamToggle = { running ->
                screamServiceRunning = running
                val intent = Intent(context, ScreamDetectionService::class.java)
                if (running) {
                    context.startForegroundService(intent)
                } else {
                    context.stopService(intent)
                }
            },
            onFallToggle = { running ->
                fallServiceRunning = running
                val intent = Intent(context, FallDetectionService::class.java)
                if (running) {
                    context.startForegroundService(intent)
                } else {
                    context.stopService(intent)
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // ── Manual Override Buttons ─────────────────────────
        ManualOverrideCard(context)

        Spacer(Modifier.height(16.dp))

        // ── Live Event Log ──────────────────────────────────
        EventLogCard(eventLog)

        Spacer(Modifier.height(24.dp))
    }
}

// ═════════════════════════════════════════════════════════════
// Giant Status Indicator
// ═════════════════════════════════════════════════════════════

@Composable
private fun StatusIndicatorCard(lastReason: String) {
    val (statusText, statusColor, statusEmoji) = when (lastReason) {
        "SCREAM_DETECTED" -> Triple("SCREAM DETECTED", AccentRed, "🔴")
        "FALL_DETECTED" -> Triple("FALL DETECTED", AmberWarn, "🟡")
        "IDLE" -> Triple("ALL SYSTEMS IDLE", GreenDot, "🟢")
        else -> Triple(lastReason, CyanInfo, "🔵")
    }

    val borderColor = statusColor.copy(alpha = 0.5f)
    val bgColor = statusColor.copy(alpha = 0.08f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusEmoji,
                fontSize = 64.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "Last Detection Status",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════
// Network Status Card
// ═════════════════════════════════════════════════════════════

@Composable
private fun NetworkStatusCard(hasInternet: Boolean, networkType: String) {
    val borderColor = if (hasInternet) GreenBorder else AmberBorder
    val bgColor = if (hasInternet) GreenBg else AmberBg
    val statusDotColor = if (hasInternet) GreenDot else AmberWarn
    val statusLabel = if (hasInternet) "ONLINE" else "OFFLINE / MESH"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(bgColor.value)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(borderColor.value), RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(statusDotColor),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NETWORK: $statusLabel",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Transport: $networkType",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = if (hasInternet) "Direct API" else "Bridgefy Mesh",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════
// Service Controls – Start / Stop toggles
// ═════════════════════════════════════════════════════════════

@Composable
private fun ServiceControlsCard(
    context: Context,
    screamRunning: Boolean,
    fallRunning: Boolean,
    onScreamToggle: (Boolean) -> Unit,
    onFallToggle: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "SERVICE CONTROLS",
                color = PurpleService,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Scream Detection toggle
            ServiceToggleRow(
                label = "Scream Detection (YAMNet AI)",
                isRunning = screamRunning,
                onToggle = onScreamToggle,
            )

            Spacer(Modifier.height(12.dp))

            // Fall Detection toggle
            ServiceToggleRow(
                label = "Fall Detection (Accelerometer)",
                isRunning = fallRunning,
                onToggle = onFallToggle,
            )
        }
    }
}

@Composable
private fun ServiceToggleRow(
    label: String,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isRunning) GreenDot else Color(0xFF666666)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = isRunning,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GreenDot,
                checkedTrackColor = GreenDot.copy(alpha = 0.3f),
                uncheckedThumbColor = Color(0xFF999999),
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

// ═════════════════════════════════════════════════════════════
// Manual Override – Simulate SOS broadcasts
// ═════════════════════════════════════════════════════════════

@Composable
private fun ManualOverrideCard(context: Context) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "MANUAL OVERRIDES",
                color = AccentRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = "Broadcast the exact same intents the real services send. " +
                        "No yelling or phone-dropping required.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // ── Simulate Scream Button ──────────────────────
            Button(
                onClick = {
                    val intent = Intent(ScreamDetectionService.ACTION_SOS_TRIGGERED).apply {
                        putExtra(ScreamDetectionService.EXTRA_REASON, "SCREAM_DETECTED")
                        putExtra("LABEL", "Scream")
                        putExtra("SCORE", 0.92f)
                    }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed.copy(alpha = 0.15f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(1.dp, AccentRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            ) {
                Text(
                    text = "🎤  SIMULATE SCREAM SOS",
                    color = AccentRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Simulate Fall Button ────────────────────────
            Button(
                onClick = {
                    val intent = Intent(FallDetectionService.ACTION_SOS_TRIGGERED).apply {
                        putExtra(FallDetectionService.EXTRA_REASON, "FALL_DETECTED")
                        putExtra("G_FORCE", 3.7f)
                        putExtra("ELAPSED_MS", 450L)
                    }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarn.copy(alpha = 0.15f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(1.dp, AmberWarn.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            ) {
                Text(
                    text = "📱  SIMULATE FALL SOS",
                    color = AmberWarn,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Simulate Unknown SOS Button ─────────────────
            Button(
                onClick = {
                    val intent = Intent(ScreamDetectionService.ACTION_SOS_TRIGGERED).apply {
                        putExtra(ScreamDetectionService.EXTRA_REASON, "MANUAL_TRIGGER")
                    }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanInfo.copy(alpha = 0.15f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, CyanInfo.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            ) {
                Text(
                    text = "🔵  SIMULATE MANUAL TRIGGER",
                    color = CyanInfo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// Live Event Log
// ═════════════════════════════════════════════════════════════

@Composable
private fun EventLogCard(events: List<DiagEvent>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LIVE EVENT LOG",
                    color = CyanInfo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "${events.size} events",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (events.isEmpty()) {
                Text(
                    text = "No events yet. Start a service or use the\nmanual overrides above to generate events.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            } else {
                events.forEach { event ->
                    EventLogRow(event)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EventLogRow(event: DiagEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(event.color.copy(alpha = 0.06f))
            .border(0.5.dp, event.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Timestamp
        Text(
            text = event.timestamp,
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(90.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.reason,
                color = event.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = event.detail,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
