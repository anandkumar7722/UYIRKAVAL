package com.hacksrm.nirbhay.screens.Stealth_Dashboard
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens  (exact values from Figma dev mode)
// ─────────────────────────────────────────────────────────────────────────────
private val BgDeep         = Color(0xFF1A0F0F)
private val BgCard         = Color(0xFF2A1818)
private val BorderSubtle   = Color(0xFF221111)
private val TextHeading    = Color(0xFFF1F5F9)
private val TextPrimary    = Color(0xFFE2E8F0)
private val TextSecondary  = Color(0xFF64748B)
private val TextMono       = Color(0xFF475569)
private val GreenActive    = Color(0xFF10B981)
private val GreenGlow      = Color(0xFF34D399)
private val WaveRedHigh    = Color(0x99EC1313)   // 0.6 opacity
private val WaveRedMid     = Color(0x80EC1313)   // 0.5 opacity
private val WaveRedLow     = Color(0x66EC1313)   // 0.4 opacity
private val WaveRedFaint   = Color(0x4DEC1313)   // 0.3 opacity

// Remote asset URLs (expire 7 days – replace with local drawables in production)
private const val MAP_IMAGE_URL    = "https://www.figma.com/api/mcp/asset/0ee17427-cc6d-4c73-8e43-db161bcaf5a9"
private const val SETTINGS_URL     = "https://www.figma.com/api/mcp/asset/a794ba48-882f-47e2-9cfc-88c6971e3664"
private const val LOCATION_URL     = "https://www.figma.com/api/mcp/asset/b18e5968-ce13-4199-bda5-4dbc5b2e6e3d"
private const val CHEVRON_URL      = "https://www.figma.com/api/mcp/asset/e2aff5a0-49bf-462d-9fae-cda8aff84f7c"
private const val MESH_URL         = "https://www.figma.com/api/mcp/asset/8e6533d6-af7e-41d9-8f66-8691ee25f60e"

// Waveform bar data: (height in dp, opacity factor)
private val WAVE_BARS = listOf(
    12f to WaveRedLow,  20f to WaveRedLow,  32f to WaveRedLow,
    16f to WaveRedMid,  24f to WaveRedHigh, 12f to WaveRedLow,
    8f  to WaveRedFaint,20f to WaveRedLow,  28f to WaveRedMid,
    16f to WaveRedHigh, 12f to WaveRedLow,  8f  to WaveRedFaint,
    12f to WaveRedLow,  20f to WaveRedLow,  32f to WaveRedLow,
    16f to WaveRedMid,  24f to WaveRedHigh, 12f to WaveRedLow,
    8f  to WaveRedFaint,20f to WaveRedLow,  28f to WaveRedMid,
    16f to WaveRedHigh, 12f to WaveRedLow,  8f  to WaveRedFaint,
)

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────
@Preview(showSystemUi = true, showBackground = true, backgroundColor = 0xFF1A0F0F)
@Composable
fun StealthDashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Header / Status Bar
        StealthHeader(onSettingsClick = { /* TODO */ })

        // 2. Map Image (full-width, aspect ratio ~390:340)
        MapSection()

        // 3. Main Content Area
        MainContentArea()

        // 4. Bottom Navigation
        // StealthBottomNav(selectedIndex = 0)  // removed — using common shared nav
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header  — pt:48  pb:16  px:24
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StealthHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title + status
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text          = "Nirbhay", // changed from "SHE-SHIELD"
                color         = TextHeading,
                fontSize      = 20.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                lineHeight    = 28.sp
            )

            // "Secure Environment" with pulsing green dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing dot
                Box(modifier = Modifier.size(8.dp)) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(GreenGlow.copy(alpha = 0.75f))
                    )
                    // Inner solid dot
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(GreenActive)
                    )
                }
                Text(
                    text          = "SECURE ENVIRONMENT",
                    color         = GreenActive,
                    fontSize      = 12.sp,
                    fontWeight    = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    lineHeight    = 16.sp
                )
            }
        }

        // Settings / menu button — neumorphic 40×40 circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BgCard)
                .shadow(
                    elevation    = 0.dp,
                    shape        = CircleShape,
                    ambientColor = Color(0xFF110909),
                    spotColor    = Color(0xFF331E1E)
                )
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = SETTINGS_URL,
                contentDescription = "Settings",
                modifier = Modifier.size(17.dp, 17.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Map Section  — full-width, aspect ratio ~596:520 (≈ height 340dp for 390dp wide)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MapSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        // Globe / map image
        AsyncImage(
            model = MAP_IMAGE_URL,
            contentDescription = "Map view",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom fade overlay so content below blends in
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BgDeep)
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Content Area  — px:24
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainContentArea() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Live Risk Monitor waveform
        LiveRiskMonitor()

        Spacer(Modifier.height(8.dp))

        // Quick access feature rows
        QuickAccessFeatures()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live Risk Monitor — waveform visualizer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LiveRiskMonitor() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text          = "LIVE RISK MONITOR",
                color         = TextSecondary,
                fontSize      = 12.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            Text(
                text      = "AI-Active",
                color     = TextMono,
                fontSize  = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Waveform bar chart — animated
        AnimatedWaveform()
    }
}

@Composable
fun AnimatedWaveform() {
    // Animate bars subtly up/down
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WAVE_BARS.forEachIndexed { index, (baseHeight, color) ->
                // Offset each bar slightly for wave effect
                val animatedHeight = baseHeight + (offset * 8f * ((index % 3) - 1f))
                val clampedHeight  = animatedHeight.coerceIn(4f, 48f)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(clampedHeight.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        // Left & right fade gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(BgCard, Color.Transparent, Color.Transparent, BgCard),
                        startX = 0f,
                        endX   = Float.POSITIVE_INFINITY
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Access Features
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun QuickAccessFeatures() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        FeatureRow(
            iconUrl    = LOCATION_URL,
            iconSize   = 20.dp to 20.dp,
            title      = "Last Known Location",
            subtitle   = "Updated 2m ago • GPS High Accuracy",
            onClick    = { /* TODO: open location screen */ }
        )
        FeatureRow(
            iconUrl    = MESH_URL,
            iconSize   = 24.dp to 23.dp,
            title      = "Mesh Network",
            subtitle   = "Bridgefy Active • 12 Nodes Nearby",
            onClick    = { /* TODO: open mesh network screen */ }
        )
    }
}

@Composable
fun FeatureRow(
    iconUrl  : String,
    iconSize : Pair<Dp, Dp>,
    title    : String,
    subtitle : String,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = BorderSubtle,
                shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)
            )
            .padding(top = 12.dp, bottom = 13.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Neumorphic icon box — 40×40 rounded-12
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .shadow(
                        elevation    = 5.dp,
                        shape        = RoundedCornerShape(12.dp),
                        ambientColor = Color(0xFF110909),
                        spotColor    = Color(0xFF331E1E)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = title,
                    modifier = Modifier.size(iconSize.first, iconSize.second)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = title,
                    color      = TextPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                )
                Text(
                    text       = subtitle,
                    color      = TextSecondary,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp
                )
            }
        }

        // Chevron arrow
        AsyncImage(
            model = CHEVRON_URL,
            contentDescription = "Navigate",
            modifier = Modifier.size(7.dp, 12.dp)
        )
    }
}

// Removed StealthBottomNav — common BottomNavBar is used by the app navigation host
