package com.hacksrm.nirbhay.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens
// ─────────────────────────────────────────────────────────────────────────────
private val BgDeep          = Color(0xFF0A0303)
private val BgGradTop       = Color(0xFF120404)
private val BgGradBot       = Color(0xFF050101)
private val BgCard          = Color(0xFF160707)
private val BgCardBorder    = Color(0x0DFFFFFF)   // 5% white
private val HeaderBg        = Color(0xCC160707)   // rgba(22,7,7,0.80)
private val HeaderBorder    = Color(0x14FFFFFF)   // 8% white
private val SectionBorder   = Color(0x14FFFFFF)   // 8% white
private val AccentRed       = Color(0xFFEC1313)
private val TextWhite       = Color(0xFFFFFFFF)
private val TextHeading     = Color(0xFFE2E8F0)
private val TextBody        = Color(0xFFCBD5E1)
private val TextSecondary   = Color(0xFF94A3B8)
private val TextMuted       = Color(0xFF64748B)
private val TextDimmer      = Color(0xFF475569)
private val ToggleOnBg      = Color(0xFFEC1313)
private val ToggleOffBg     = Color(0xFF334155)
private val ToggleThumb     = Color(0xFFFFFFFF)
private val ToggleThumbBorder = Color(0xFFD1D5DB)
private val GreenText       = Color(0xFF22C55E)
private val GreenBg         = Color(0x1A22C55E)
private val AmberText       = Color(0xFFF59E0B)
private val AmberBg         = Color(0x1AF59E0B)

// Remote asset URLs (replace with local drawables in production)
private const val BACK_ICON_URL       = "https://www.figma.com/api/mcp/asset/772b626c-61f6-4b05-b7fd-d8e87d4c0bc5"
private const val CHECK_ICON_URL      = "https://www.figma.com/api/mcp/asset/2e6d3eb4-df1a-41b1-9c17-9f081edacdb3"
private const val ICON_MANUAL_URL     = "https://www.figma.com/api/mcp/asset/61415139-68d8-424c-b4b4-079d88b8bf9c"
private const val ICON_SHAKE_URL      = "https://www.figma.com/api/mcp/asset/7b09d613-7ee4-44d2-a475-c0ea429d0586"
private const val ICON_SCREAM_URL     = "https://www.figma.com/api/mcp/asset/54af7218-bba7-41fc-af1b-4512a72f3b17"
private const val ICON_THRESHOLD_URL  = "https://www.figma.com/api/mcp/asset/5ab0e518-5c7b-4248-9109-4cf3daabff12"
private const val ICON_FALL_URL       = "https://www.figma.com/api/mcp/asset/b04151b5-d599-498d-828b-34ed971ec974"
private const val ICON_STEALTH_URL    = "https://www.figma.com/api/mcp/asset/55ce74c7-3bf7-4581-b79e-c312cdbe0a31"
private const val ICON_PIN_URL        = "https://www.figma.com/api/mcp/asset/99d86f0a-8b58-471b-a534-e4bc016aadb8"
private const val CHEVRON_ICON_URL    = "https://www.figma.com/api/mcp/asset/b7ee3c98-719d-43c9-9a5b-6be0dc815212"
private const val ICON_LOCATION_URL   = "https://www.figma.com/api/mcp/asset/c8c94d28-7232-4af0-b7cc-541ded741ee8"
private const val ICON_MIC_URL        = "https://www.figma.com/api/mcp/asset/c311bfa4-6636-4bd3-9559-9d7adbba80da"
private const val ICON_CAMERA_URL     = "https://www.figma.com/api/mcp/asset/ed091e9f-4079-4610-8f3a-3cacf90ae65f"

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────
@Preview(showSystemUi = true, showBackground = true, backgroundColor = 0xFF0A0303)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onSave: () -> Unit = {}
) {
    // Toggle states
    var manualOverride   by remember { mutableStateOf(true) }
    var shakeToAlert     by remember { mutableStateOf(false) }
    var screamRecognition by remember { mutableStateOf(true) }
    var fallDetection    by remember { mutableStateOf(true) }
    var stealthMode      by remember { mutableStateOf(false) }
    var sliderValue      by remember { mutableStateOf(0.70f) }  // HIGH ~70%

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgGradTop, BgGradBot)
                )
            )
    ) {
        // Scrollable content — padded top for header
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 113.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Page title & subtitle
            PageTitle()

            // ── Section 1: SOS Trigger Methods
            SectionHeader(title = "SOS Trigger Methods")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ToggleSettingRow(
                    iconUrl  = ICON_MANUAL_URL,
                    iconW    = 35.dp, iconH = 39.dp,
                    title    = "Manual Override",
                    subtitle = "Tap 5 times to activate",
                    checked  = manualOverride,
                    onToggle = { manualOverride = it }
                )
                ToggleSettingRow(
                    iconUrl  = ICON_SHAKE_URL,
                    iconW    = 42.dp, iconH = 36.dp,
                    title    = "Shake to Alert",
                    subtitle = "Violent motion detection",
                    checked  = shakeToAlert,
                    onToggle = { shakeToAlert = it }
                )
                ToggleSettingRow(
                    iconUrl  = ICON_SCREAM_URL,
                    iconW    = 40.dp, iconH = 37.dp,
                    title    = "Scream Recognition",
                    subtitle = "AI audio analysis",
                    checked  = screamRecognition,
                    onToggle = { screamRecognition = it }
                )
            }
            Spacer(Modifier.height(32.dp))

            // ── Section 2: Sensor Sensitivity
            SectionHeader(title = "Sensor Sensitivity")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Scream Threshold slider
                ScreamThresholdRow(
                    iconUrl      = ICON_THRESHOLD_URL,
                    sliderValue  = sliderValue,
                    onValueChange = { sliderValue = it }
                )
                ToggleSettingRow(
                    iconUrl  = ICON_FALL_URL,
                    iconW    = 34.dp, iconH = 38.dp,
                    title    = "Fall Detection",
                    subtitle = "Auto-trigger on impact",
                    checked  = fallDetection,
                    onToggle = { fallDetection = it }
                )
            }
            Spacer(Modifier.height(32.dp))

            // ── Section 3: Privacy & Security
            SectionHeader(title = "Privacy & Security")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ToggleSettingRow(
                    iconUrl  = ICON_STEALTH_URL,
                    iconW    = 40.dp, iconH = 38.dp,
                    title    = "Stealth Mode",
                    subtitle = "Hide app icon & notifications",
                    checked  = stealthMode,
                    onToggle = { stealthMode = it }
                )
                ChevronSettingRow(
                    iconUrl  = ICON_PIN_URL,
                    iconW    = 34.dp, iconH = 39.dp,
                    title    = "PIN Lock Setup",
                    subtitle = "Require PIN to cancel SOS",
                    onClick  = { /* TODO: navigate to PIN setup */ }
                )
            }
            Spacer(Modifier.height(32.dp))

            // ── Section 4: System Permissions
            SectionHeader(title = "System Permissions")
            PermissionsSection()
            Spacer(Modifier.height(40.dp))

            // ── Version string
            Text(
                text          = "SHE-SHIELD v2.4.1 (Build 890)",
                color         = TextDimmer,
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Normal,
                letterSpacing = 1.sp,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                textAlign     = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // ── Sticky header overlay
        SettingsHeader(onBack = onBack, onSave = onSave)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sticky Header  — back | SETTINGS | check
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsHeader(onBack: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .border(
                width = 1.dp,
                color = HeaderBorder,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = BACK_ICON_URL,
                contentDescription = "Back",
                modifier = Modifier.size(16.dp)
            )
        }

        // Title
        Text(
            text          = "SETTINGS",
            color         = TextHeading,
            fontSize      = 18.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.45.sp
        )

        // Save / check button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onSave),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = CHECK_ICON_URL,
                contentDescription = "Save",
                modifier = Modifier.size(18.dp, 14.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page title block
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PageTitle() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text       = "Advanced Controls",
            color      = TextWhite,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        )
        Text(
            text       = "Configure SHE-SHIELD AI & sensors",
            color      = TextSecondary,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header with red label + bottom divider
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = SectionBorder,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(start = 24.dp, end = 24.dp, bottom = 9.dp, top = 0.dp)
    ) {
        Text(
            text          = title.uppercase(),
            color         = AccentRed,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            lineHeight    = 16.sp
        )
    }
    Spacer(Modifier.height(16.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Toggle setting row  (icon | title + subtitle | toggle)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ToggleSettingRow(
    iconUrl  : String,
    iconW    : androidx.compose.ui.unit.Dp,
    iconH    : androidx.compose.ui.unit.Dp,
    title    : String,
    subtitle : String,
    checked  : Boolean,
    onToggle : (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = title,
                modifier = Modifier.size(iconW, iconH)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = title,
                    color      = TextWhite,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                )
                Text(
                    text       = subtitle,
                    color      = TextMuted,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp
                )
            }
        }

        // Custom toggle
        ToggleSwitch(checked = checked, onToggle = onToggle)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chevron setting row  (icon | title + subtitle | chevron)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChevronSettingRow(
    iconUrl  : String,
    iconW    : androidx.compose.ui.unit.Dp,
    iconH    : androidx.compose.ui.unit.Dp,
    title    : String,
    subtitle : String,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = title,
                modifier = Modifier.size(iconW, iconH)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = title,
                    color      = TextWhite,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                )
                Text(
                    text       = subtitle,
                    color      = TextMuted,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp
                )
            }
        }

        AsyncImage(
            model = CHEVRON_ICON_URL,
            contentDescription = "Navigate",
            modifier = Modifier.size(7.dp, 12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Toggle Switch  (44×24, thumb 20×20)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ToggleSwitch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(if (checked) ToggleOnBg else ToggleOffBg)
            .clickable { onToggle(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (checked) 22.dp else 2.dp, top = 2.dp, bottom = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(ToggleThumb)
                .then(
                    if (!checked) Modifier.border(1.dp, ToggleThumbBorder, CircleShape)
                    else Modifier
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scream Threshold slider row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ScreamThresholdRow(
    iconUrl       : String,
    sliderValue   : Float,
    onValueChange : (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Label row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = "Scream Threshold",
                    modifier = Modifier.size(36.dp, 38.dp)
                )
                Text(
                    text       = "Scream Threshold",
                    color      = TextWhite,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                )
            }
            Text(
                text       = when {
                    sliderValue >= 0.66f -> "HIGH"
                    sliderValue >= 0.33f -> "MED"
                    else                 -> "LOW"
                },
                color      = AccentRed,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Slider + labels
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Custom slider track + thumb
            androidx.compose.material3.Slider(
                value         = sliderValue,
                onValueChange = onValueChange,
                modifier      = Modifier.fillMaxWidth(),
                colors        = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor           = TextWhite,
                    activeTrackColor     = TextWhite,
                    inactiveTrackColor   = Color(0x1AFFFFFF)
                )
            )

            // Low / High labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text      = "LOW",
                    color     = TextDimmer,
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text      = "HIGH",
                    color     = TextDimmer,
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// System Permissions section — 3 permission cards
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PermissionsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PermissionCard(
            iconUrl    = ICON_LOCATION_URL,
            iconW      = 13.dp, iconH = 13.dp,
            label      = "Precise Location",
            statusText = "ALWAYS",
            statusColor = GreenText,
            statusBg    = GreenBg
        )
        PermissionCard(
            iconUrl    = ICON_MIC_URL,
            iconW      = 8.dp, iconH = 11.dp,
            label      = "Microphone",
            statusText = "GRANTED",
            statusColor = GreenText,
            statusBg    = GreenBg
        )
        PermissionCard(
            iconUrl    = ICON_CAMERA_URL,
            iconW      = 12.dp, iconH = 9.dp,
            label      = "Camera",
            statusText = "ASK",
            statusColor = AmberText,
            statusBg    = AmberBg
        )
    }
}

@Composable
fun PermissionCard(
    iconUrl     : String,
    iconW       : androidx.compose.ui.unit.Dp,
    iconH       : androidx.compose.ui.unit.Dp,
    label       : String,
    statusText  : String,
    statusColor : Color,
    statusBg    : Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = label,
                modifier = Modifier.size(iconW, iconH)
            )
            Text(
                text       = label,
                color      = TextBody,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            )
        }

        // Status badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text       = statusText,
                color      = statusColor,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}