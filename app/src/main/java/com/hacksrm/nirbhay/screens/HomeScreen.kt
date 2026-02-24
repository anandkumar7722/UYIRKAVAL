package com.hacksrm.nirbhay.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF221010)
private val BgCard        = Color(0x99331919)
private val BgCardSolid   = Color(0xF2331919)
private val BorderDark    = Color(0xFF482323)
private val AccentRed     = Color(0xFFEC1313)
private val AccentRedDark = Color(0xFFB00E0E)
private val TextWhite     = Color(0xFFFFFFFF)
private val TextMuted     = Color(0xFFC99292)
private val TextWhite80   = Color(0xCCFFFFFF)
private val GreenDot      = Color(0xFF22C55E)
private val GreenText     = Color(0xFF4ADE80)
private val GreenBg       = Color(0x3314532D)
private val GreenBorder   = Color(0x3322C55E)

// Only keep avatar URL — icons replaced with Material Icons
private const val AVATAR_URL = "https://www.figma.com/api/mcp/asset/0d38b18a-8147-4c5c-8fbd-a0d56b41d1c3"

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────
@Preview(showSystemUi = true, showBackground = true, backgroundColor = 0xFF221010)
@Composable
fun HomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Background radial glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x26EC1313), Color(0x00EC1313)),
                    center = Offset.Zero,
                    radius = size.minDimension * 1.4f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x80482323), Color(0x00482323)),
                    center = Offset(size.width, size.height),
                    radius = size.minDimension * 1.4f
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSection()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                SafetyScoreSection()
                SosButton(onClick = { /* TODO */ })
                RiskAlertCard(onViewMap = { /* TODO */ })
            }

            BottomNavBar(selectedIndex = 0)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with green online dot
            Box(modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BorderDark)
                        .border(2.dp, BorderDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = AVATAR_URL,
                        contentDescription = "User avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(BgDark)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(GreenDot)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Welcome back,",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
                Text(
                    text = "Sarah Jenkins",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                )
            }
        }

        // Notification bell using Material Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BgCard)
                .border(1.dp, Color(0x0DFFFFFF), CircleShape)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
            // Red unread badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentRed)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Safety Score  — FIX: inner Box now has contentAlignment = Center
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SafetyScoreSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(192.dp),
            contentAlignment = Alignment.Center   // ← ensures ring & content stack centered
        ) {
            // Draw progress arc ring on canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx   = 6.dp.toPx()
                val halfStroke = strokePx / 2

                // Background track
                drawArc(
                    color      = Color(0x33EC1313),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter  = false,
                    topLeft    = Offset(halfStroke, halfStroke),
                    size       = Size(size.width - strokePx, size.height - strokePx),
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                // Foreground arc — 98%
                drawArc(
                    color      = AccentRed,
                    startAngle = -90f,
                    sweepAngle = 360f * 0.98f,
                    useCenter  = false,
                    topLeft    = Offset(halfStroke, halfStroke),
                    size       = Size(size.width - strokePx, size.height - strokePx),
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                // Inner decorative ring
                drawCircle(
                    color  = Color(0x33EC1313),
                    radius = (size.minDimension / 2) - 9.6.dp.toPx(),
                    style  = Stroke(width = 1.dp.toPx())
                )
            }

            // ── FIX: dark inner fill with ALL content centered ──
            Box(
                modifier = Modifier
                    .size(176.dp)                        // 192 − 8dp inset × 2
                    .clip(CircleShape)
                    .background(BgDark),
                contentAlignment = Alignment.Center      // ← THIS centers the Column inside
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,  // ← ensures vertical centering
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Shield icon using Material Icon
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Shield",
                        tint = AccentRed,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text          = "98",
                        color         = TextWhite,
                        fontSize      = 36.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = (-1.8).sp,
                        lineHeight    = 40.sp,
                        textAlign     = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text          = "SAFETY SCORE",
                        color         = TextMuted,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        textAlign     = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // "Status: Secure" badge
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(GreenBg)
                .border(1.dp, GreenBorder, CircleShape)
                .padding(horizontal = 13.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GreenDot)
            )
            Text(
                text          = "STATUS: SECURE",
                color         = GreenText,
                fontSize      = 12.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SOS Button  — FIX: proper height, truly centered content, Material Icon
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SosButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(100.dp)                          // ← comfortable height for font
            .shadow(
                elevation    = 20.dp,
                shape        = RoundedCornerShape(28.dp),
                ambientColor = Color(0x4DEC1313),
                spotColor    = Color(0x4DEC1313)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AccentRed, AccentRedDark),
                    start  = Offset(0f, 0f),
                    end    = Offset(600f, 600f)
                )
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center          // ← centers Row inside Box
    ) {
        // Centered row with icon + text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center  // ← centers items horizontally
        ) {
            // ── FIX: use Material Icon instead of broken Figma asset URL ──
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "SOS",
                    tint = TextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Text column — centered
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text          = "SOS ALERT",
                    color         = TextWhite,
                    fontSize      = 22.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    textAlign     = TextAlign.Center
                )
                Text(
                    text      = "Press for immediate help",
                    color     = TextWhite80,
                    fontSize  = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Chevron at top-end using Material Icon
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextWhite80,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Risk Alert Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RiskAlertCard(onViewMap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AccentRed)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                .background(BgCard)
                .border(1.dp, AccentRed, RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                .padding(start = 16.dp, end = 17.dp, top = 17.dp, bottom = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text       = "High Risk Area Detected",
                    color      = TextWhite,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                )
                Text(
                    text       = "2 blocks away. Avoid 5th Avenue.",
                    color      = TextMuted,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp
                )
            }

            Text(
                text       = "View Map",
                color      = AccentRed,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.clickable(onClick = onViewMap)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation  — FIX: replaced broken Figma asset URLs with Material Icons
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BottomNavBar(selectedIndex: Int = 0) {
    // Using Material Icons directly — guaranteed visible
    val navItems = listOf(
        Pair("Home",    Icons.Filled.Home),
        Pair("Map",     Icons.Filled.Place),
        Pair("Network", Icons.Filled.Share),
        Pair("Profile", Icons.Filled.Person),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCardSolid)
            .border(width = 1.dp, color = BorderDark, shape = RoundedCornerShape(0.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (label, icon) ->
                val isSelected = index == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) AccentRed else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text          = label,
                        color         = if (isSelected) AccentRed else TextMuted,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 0.25.sp
                    )
                }
            }
        }
    }
}