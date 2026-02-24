package com.hacksrm.nirbhay.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
// Design Tokens  (exact values from Figma dev mode)
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF221010)
private val BgCard        = Color(0x99331919)   // rgba(51,25,25,0.60)
private val BgCardSolid   = Color(0xF2331919)   // rgba(51,25,25,0.95) – bottom nav
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

// Remote asset URLs (from Figma – expire after 7 days).
// TODO: Replace with local drawable resources before production.
private const val AVATAR_URL      = "https://www.figma.com/api/mcp/asset/0d38b18a-8147-4c5c-8fbd-a0d56b41d1c3"
private const val BELL_URL        = "https://www.figma.com/api/mcp/asset/ffc8fb80-21da-4f5c-b2d5-a57f2edbf0f3"
private const val SHIELD_URL      = "https://www.figma.com/api/mcp/asset/755e0d81-e855-406a-99d9-8d3980498823"
private const val SOS_ICON_URL    = "https://www.figma.com/api/mcp/asset/6dc5b7ad-010a-4207-b332-300a07880225"
private const val CHEVRON_URL     = "https://www.figma.com/api/mcp/asset/fa12e297-6dc6-4c96-9f64-9822e59cf571"
private const val NAV_HOME_URL    = "https://www.figma.com/api/mcp/asset/da7e27fc-4992-4786-bbb4-33553869dd61"
private const val NAV_MAP_URL     = "https://www.figma.com/api/mcp/asset/704050bd-bca7-40fc-a311-1a8e362b50b3"
private const val NAV_NETWORK_URL = "https://www.figma.com/api/mcp/asset/8d7f0667-ff9c-4184-8d51-e153ac5f5aa3"
private const val NAV_PROFILE_URL = "https://www.figma.com/api/mcp/asset/d703b278-13b6-4eaa-b642-203bcfd167dc"

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
        // Background radial glows (top-left red, bottom-right dark red)
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
                SosButton(onClick = { /* TODO: trigger SOS flow */ })
                RiskAlertCard(onViewMap = { /* TODO: open map screen */ })
            }

            BottomNavBar(selectedIndex = 0)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header  — pt:32  pb:24  px:24
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
        // Avatar + greeting
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 40×40 avatar with green online dot (bottom-end)
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
                // Green online dot — 12×12 with 2dp BgDark border
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

        // Notification bell — 40×40 glassmorphism circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BgCard)
                .border(1.dp, Color(0x0DFFFFFF), CircleShape)
                .clickable { /* open notifications */ },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = BELL_URL,
                contentDescription = "Notifications",
                modifier = Modifier.size(13.dp, 17.dp)
            )
            // Red unread badge — 8×8
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentRed)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Safety Score  — 192×192 circle gauge + status badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SafetyScoreSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(192.dp)
                .shadow(
                    elevation = 50.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x1AEC1313),
                    spotColor = Color(0x1AEC1313)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Canvas: outer arc + inner decorative ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx  = 6.dp.toPx()
                val halfStroke = strokePx / 2

                // Outer red arc — 98% sweep
                drawArc(
                    color      = AccentRed,
                    startAngle = -90f,
                    sweepAngle = 360f * 0.98f,
                    useCenter  = false,
                    topLeft    = Offset(halfStroke, halfStroke),
                    size       = Size(size.width - strokePx, size.height - strokePx),
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Inner decorative ring — inset 9.6dp, rgba(236,19,19,0.2)
                drawCircle(
                    color  = Color(0x33EC1313),
                    radius = (size.minDimension / 2) - 9.6.dp.toPx(),
                    style  = Stroke(width = 1.dp.toPx())
                )
            }

            // Dark inner fill — diameter 176dp (192 - 8dp inset × 2)
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(BgDark),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = SHIELD_URL,
                        contentDescription = "Shield",
                        modifier = Modifier.size(20.dp, 29.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text          = "98",
                        color         = TextWhite,
                        fontSize      = 36.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = (-1.8).sp,
                        lineHeight    = 40.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text          = "SAFETY SCORE",
                        color         = TextMuted,
                        fontSize      = 12.sp,
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        lineHeight    = 16.sp
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
                letterSpacing = 0.6.sp,
                lineHeight    = 16.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SOS Button  — full width, h:122, rounded-32, red gradient + red glow shadow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SosButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(122.dp)
            .shadow(
                elevation    = 20.dp,
                shape        = RoundedCornerShape(32.dp),
                ambientColor = Color(0x4DEC1313),
                spotColor    = Color(0x4DEC1313)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AccentRed, AccentRedDark),
                    start  = Offset(0f, 0f),
                    end    = Offset(600f, 600f)   // ~161.7° diagonal from Figma
                )
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Frosted glass SOS icon circle — 48×48
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = SOS_ICON_URL,
                    contentDescription = "SOS icon",
                    modifier = Modifier.size(28.dp, 13.dp)
                )
            }

            Column {
                Text(
                    text          = "SOS ALERT",
                    color         = TextWhite,
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    lineHeight    = 32.sp,
                    textAlign     = TextAlign.Center
                )
                Text(
                    text       = "Press for immediate help",
                    color      = TextWhite80,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                    textAlign  = TextAlign.Center
                )
            }
        }

        // Chevron — positioned top-end (right:24, top:32)
        AsyncImage(
            model = CHEVRON_URL,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 24.dp)
                .size(7.dp, 12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Risk Alert Card  — glassmorphism + 4dp left red accent border
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RiskAlertCard(onViewMap: () -> Unit) {
    // Outer red box provides the 4dp left accent border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(AccentRed)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)   // 4dp = left border thickness
                .clip(RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp))
                .background(BgCard)
                .border(
                    1.dp, AccentRed,
                    RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
                )
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
                lineHeight = 20.sp,
                modifier   = Modifier.clickable(onClick = onViewMap)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation  — h:71  pt:13  pb:24  px:24
// ─────────────────────────────────────────────────────────────────────────────
data class NavItem(
    val label   : String,
    val iconUrl : String,
    val iconW   : Float,
    val iconH   : Float
)

@Composable
fun BottomNavBar(selectedIndex: Int = 0) {
    val items = listOf(
        NavItem("Home",    NAV_HOME_URL,    16f, 18f),
        NavItem("Map",     NAV_MAP_URL,     18f, 18f),
        NavItem("Network", NAV_NETWORK_URL, 24f, 23f),
        NavItem("Profile", NAV_PROFILE_URL, 16f, 16f),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCardSolid)
            .border(
                width = 1.dp,
                color = BorderDark,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 13.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { /* TODO: navigate to $index */ }
                ) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = item.label,
                        modifier = Modifier.size(item.iconW.dp, item.iconH.dp)
                    )
                    Text(
                        text          = item.label,
                        color         = if (isSelected) AccentRed else TextMuted,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 0.25.sp,
                        lineHeight    = 15.sp
                    )
                }
            }
        }
    }
}