package com.hacksrm.nirbhay.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens
// ─────────────────────────────────────────────────────────────────────────────
private val BgBlack        = Color(0xFF050505)
private val BgCard         = Color(0x4D2A0A0A)   // rgba(42,10,10,0.30)
private val RingRed        = Color(0xFFDC2626)
private val RingRedGlow    = Color(0x33DC2626)    // 20% opacity glow
private val AccentRed      = Color(0xFFEC1313)
private val AccentRedDot   = Color(0xFFEF4444)
private val GreenDot       = Color(0xFF22C55E)
private val AmberDot       = Color(0xFFF59E0B)
private val TextWhite      = Color(0xFFFFFFFF)
private val TextLight      = Color(0xFFE2E8F0)
private val TextMuted      = Color(0xFFCBD5E1)
private val TextDim        = Color(0xFF94A3B8)
private val TextFaint      = Color(0x4DFFFFFF)    // 30% white
private val WaveRed60      = Color(0x99EC1313)
private val WaveRed80      = Color(0xCCEC1313)
private val SliderBg       = Color(0x0DFFFFFF)    // 5% white
private val SliderBorder   = Color(0x1AFFFFFF)    // 10% white

// Asset URLs (expire 7 days – replace with local drawables in production)
private const val BACK_ICON_URL    = "https://www.figma.com/api/mcp/asset/2088539c-baba-4f4a-8891-4fbe50fcfec3"
private const val TRIGGER_ICON_URL = "https://www.figma.com/api/mcp/asset/5d54dbce-a973-44e9-baae-381fa39aef81"
private const val MIC_ICON_URL     = "https://www.figma.com/api/mcp/asset/49fdd450-5b81-4c33-9f9f-72bbec93b9b6"
private const val GPS_ICON_URL     = "https://www.figma.com/api/mcp/asset/6fcc1376-35ef-4fc8-8e58-5a8832c02e9a"
private const val MESH_ICON_URL    = "https://www.figma.com/api/mcp/asset/9843d3ce-9aa6-459b-9c22-c6e331b5197f"
private const val SPINNER_ICON_URL = "https://www.figma.com/api/mcp/asset/35d8b501-a038-4671-b404-d7739d6fe74c"
private const val SLIDER_ICON_URL  = "https://www.figma.com/api/mcp/asset/e14b50b2-661f-4fc4-be53-27060b7e0240"
private const val RING_SVG_URL     = "https://www.figma.com/api/mcp/asset/da04a1e6-5087-453c-b0c9-0ed6b61fab50"

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────
@Preview(showSystemUi = true, showBackground = true, backgroundColor = 0xFF050505)
@Composable
fun SosCountdownScreen(
    initialSeconds: Int = 9,
    onBack: () -> Unit = {},
    onCancelled: () -> Unit = {}
) {
    // Countdown timer state
    var secondsLeft by remember { mutableStateOf(initialSeconds) }
    val totalSeconds = remember { initialSeconds }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        // TODO: trigger SOS action when reaches 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {
        // Bottom red gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x007F1D1D), Color(0x337F1D1D))
                    )
                )
        )

        // ── Top Nav ──────────────────────────────────────────────────────────
        TopNavBar(onBack = onBack)

        // ── "SOS TRIGGERED" heading + trigger reason ──────────────────────
        EmergencyStatus(modifier = Modifier.padding(top = 88.dp))

        // ── Countdown Ring ────────────────────────────────────────────────
        CountdownRingSection(
            secondsLeft  = secondsLeft,
            totalSeconds = totalSeconds,
            modifier     = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 202.dp)
        )

        // ── Status Cards ──────────────────────────────────────────────────
        StatusCardsPanel(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 597.dp, start = 25.dp, end = 25.dp)
        )

        // ── Slide to Cancel ───────────────────────────────────────────────
        SlideToCancelButton(
            onCancelled = onCancelled,
            modifier    = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp, start = 32.dp, end = 32.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Nav  — back button | "SHE-SHIELD LIVE" | spacer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TopNavBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button — frosted glass circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = BACK_ICON_URL,
                contentDescription = "Back",
                modifier = Modifier.size(16.dp)
            )
        }

        // "SHE-SHIELD LIVE" with pulsing red dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pulsing dot
            val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue  = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentRedDot.copy(alpha = dotAlpha))
            )
            Text(
                text          = "SHE-SHIELD LIVE",
                color         = TextWhite,
                fontSize      = 14.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.7.sp
            )
        }

        // Invisible spacer to balance layout
        Spacer(Modifier.size(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Emergency Status  — "SOS TRIGGERED" + trigger reason pill
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyStatus(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // "SOS TRIGGERED" with red glow shadow
        Text(
            text          = "SOS TRIGGERED",
            color         = RingRed,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 6.sp,
            modifier      = Modifier.drawBehind {
                // Simulate text glow via a red shadow circle
                drawCircle(
                    color  = Color(0x80DC2626),
                    radius = size.minDimension * 1.5f,
                    style  = Stroke(width = 0f)
                )
            }
        )

        // Trigger reason pill — glassmorphism
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .padding(horizontal = 25.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = TRIGGER_ICON_URL,
                contentDescription = "Trigger",
                modifier = Modifier.size(13.dp, 11.dp)
            )
            Text(
                text = buildString {
                    append("Trigger reason: ")
                },
                color      = TextMuted,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text       = "Scream detected",
                color      = TextWhite,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Countdown Ring Section  — animated arc ring + big timer number
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CountdownRingSection(
    secondsLeft  : Int,
    totalSeconds : Int,
    modifier     : Modifier = Modifier
) {
    val sweepFraction = secondsLeft.toFloat() / totalSeconds.toFloat()

    // Animate the sweep angle smoothly
    val animatedSweep by animateFloatAsState(
        targetValue    = sweepFraction * 360f,
        animationSpec  = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label          = "countdown"
    )

    Box(
        modifier = modifier.height(395.dp),
        contentAlignment = Alignment.Center
    ) {
        // Red radial glow behind the ring
        Box(
            modifier = Modifier
                .size(288.dp)
                .blur(40.dp)
                .clip(CircleShape)
                .background(Color(0x33DC2626))
        )

        // Ring + number
        Box(
            modifier = Modifier.size(288.dp),
            contentAlignment = Alignment.Center
        ) {
            // Canvas: draw the progress arc
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val strokeWidth = 12.dp.toPx()
                val halfStroke  = strokeWidth / 2
                val arcSize     = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft     = Offset(halfStroke, halfStroke)

                // Background track (dark)
                drawArc(
                    color      = Color(0x33DC2626),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Foreground progress arc (red)
                drawArc(
                    color      = RingRed,
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Inner content: number + label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Big countdown number with gradient text effect
                Text(
                    text          = secondsLeft.toString().padStart(2, '0'),
                    fontSize      = 120.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = (-6).sp,
                    color         = TextWhite,
                    textAlign     = TextAlign.Center,
                    lineHeight    = 120.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text          = "SECONDS",
                    color         = TextFaint,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 5.6.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status Cards Panel  — audio / GPS / mesh status rows
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatusCardsPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .border(1.dp, SliderBorder, RoundedCornerShape(24.dp))
            .padding(17.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {

            // Row 1: Capturing Audio
            StatusRow(
                dotColor  = AccentRed,
                iconUrl   = MIC_ICON_URL,
                iconSize  = 11.dp to 14.dp,
                label     = "Capturing Audio...",
                trailing  = {
                    // Animated audio bars
                    AudioBarsIndicator()
                }
            )

            // Row 2: GPS Location Locked
            StatusRow(
                dotColor = GreenDot,
                iconUrl  = GPS_ICON_URL,
                iconSize = 16.dp to 16.dp,
                label    = "GPS Location Locked",
                trailing = {
                    Text(
                        text      = "34.05°N, 118.24°W",
                        color     = TextDim,
                        fontSize  = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            )

            // Row 3: Mesh Network Initializing
            StatusRow(
                dotColor = AmberDot,
                iconUrl  = MESH_ICON_URL,
                iconSize = 18.dp to 17.dp,
                label    = "Mesh Network Initializing",
                trailing = {
                    // Spinning refresh icon
                    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                        initialValue = 0f,
                        targetValue  = 360f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                        label = "spinAngle"
                    )
                    AsyncImage(
                        model = SPINNER_ICON_URL,
                        contentDescription = "Loading",
                        modifier = Modifier
                            .size(9.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            )
        }
    }
}

@Composable
fun StatusRow(
    dotColor : Color,
    iconUrl  : String,
    iconSize : Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>,
    label    : String,
    trailing : @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Icon
        AsyncImage(
            model = iconUrl,
            contentDescription = label,
            modifier = Modifier.size(iconSize.first, iconSize.second)
        )
        // Label
        Text(
            text       = label,
            color      = TextLight,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(1f)
        )
        // Trailing widget
        trailing()
    }
}

@Composable
fun AudioBarsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "audioBars")
    val anim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "audioPulse"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        listOf(8f, 16f, 12f).forEachIndexed { i, baseH ->
            val animH = (baseH * (0.6f + anim * 0.4f * ((i + 1) % 2 + 0.5f))).coerceIn(4f, 16f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animH.dp)
                    .clip(CircleShape)
                    .background(if (i == 1) WaveRed80 else WaveRed60)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide to Cancel Button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SlideToCancelButton(
    onCancelled : () -> Unit,
    modifier    : Modifier = Modifier
) {
    // Track the thumb drag offset
    var thumbOffsetX by remember { mutableStateOf(0f) }
    val trackWidth   = 326.dp
    val thumbSize    = 64.dp
    val maxOffset    = with(androidx.compose.ui.platform.LocalDensity.current) {
        (trackWidth - thumbSize - 16.dp).toPx()
    }

    // Animate thumb back to start if not fully slid
    val animatedOffset by animateFloatAsState(
        targetValue   = thumbOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "thumbSnap"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(CircleShape)
            .background(SliderBg)
            .border(1.dp, SliderBorder, CircleShape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (thumbOffsetX >= maxOffset * 0.85f) {
                            onCancelled()
                        } else {
                            thumbOffsetX = 0f
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        thumbOffsetX = (thumbOffsetX + dragAmount).coerceIn(0f, maxOffset)
                    }
                )
            }
    ) {
        // "SLIDE TO CANCEL SOS" text (centered)
        Text(
            text          = "SLIDE TO CANCEL SOS",
            color         = TextFaint,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 2.4.sp,
            textAlign     = TextAlign.Center,
            modifier      = Modifier
                .align(Alignment.Center)
                .padding(start = 80.dp)
        )

        // Draggable white thumb
        Box(
            modifier = Modifier
                .padding(start = 9.dp, top = 8.dp, bottom = 8.dp)
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(TextWhite)
                .shadow(
                    elevation    = 20.dp,
                    shape        = CircleShape,
                    ambientColor = Color(0x4DFFFFFF),
                    spotColor    = Color(0x4DFFFFFF)
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = SLIDER_ICON_URL,
                contentDescription = "Slide",
                modifier = Modifier.size(9.dp, 14.dp)
            )
        }
    }
}