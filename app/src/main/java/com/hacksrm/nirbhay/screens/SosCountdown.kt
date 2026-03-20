package com.hacksrm.nirbhay.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hacksrm.nirbhay.LocationHelper
import com.hacksrm.nirbhay.connectivity.ConnectivityHelper
import com.hacksrm.nirbhay.sos.SOSEngine
import com.hacksrm.nirbhay.sos.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens
// ─────────────────────────────────────────────────────────────────────────────
private val BgBlack        = Color(0xFF050505)
private val BgCard         = Color(0x4D2A0A0A)   // rgba(42,10,10,0.30)
private val RingRed        = Color(0xFFDC2626)
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
private val SliderBg       = BgBlack.copy(alpha = 0.04f)    // subtler dark track (reduced opacity)
private val SliderBorder   = Color(0x14FFFFFF)    // slightly lighter border alpha
private val SliderFill     = BgBlack.copy(alpha = 0.12f)   // filled portion (darker but reduced)

// Asset URLs (expire 7 days – replace with local drawables in production)
private const val BACK_ICON_URL    = "https://www.figma.com/api/mcp/asset/2088539c-baba-4f4a-8891-4fbe50fcfec3"
private const val TRIGGER_ICON_URL = "https://www.figma.com/api/mcp/asset/5d54dbce-a973-44e9-baae-381fa39aef81"
private const val MIC_ICON_URL     = "https://www.figma.com/api/mcp/asset/49fdd450-5b81-4c33-9f9f-72bbec93b9b6"
private const val GPS_ICON_URL     = "https://www.figma.com/api/mcp/asset/6fcc1376-35ef-4fc8-8e58-5a8832c02e9a"
private const val MESH_ICON_URL    = "https://www.figma.com/api/mcp/asset/9843d3ce-9aa6-459b-9c22-c6e331b5197f"
private const val SPINNER_ICON_URL = "https://www.figma.com/api/mcp/asset/35d8b501-a038-4671-b404-d7739d6fe74c"
private const val SLIDER_ICON_URL  = "https://www.figma.com/api/mcp/asset/e14b50b2-661f-4fc4-be53-27060b7e0240"

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────
@Preview(showSystemUi = true, showBackground = true, backgroundColor = 0xFF050505)
@Composable
fun SosCountdownScreen(
    initialSeconds: Int = 9,
    triggerReason: String? = null,
    onBack: () -> Unit = {},
    onCancelled: () -> Unit = {}
) {
    // Countdown timer state
    var secondsLeft by remember { mutableStateOf(initialSeconds) }
    val totalSeconds = remember { initialSeconds }
    // Track whether the user cancelled via the slide control
    var isCancelled by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Derive the TriggerSource from the reason string (or default to BUTTON)
    val triggerSource = remember(triggerReason) {
        when (triggerReason?.uppercase()) {
            "SCREAM_DETECTED" -> TriggerSource.SCREAM
            "FALL_DETECTED"   -> TriggerSource.FALL
            "SHAKE_DETECTED"  -> TriggerSource.SHAKE
            "AUTO"            -> TriggerSource.AUTO
            "VOLUME"          -> TriggerSource.VOLUME
            "POWER"           -> TriggerSource.POWER
            else              -> TriggerSource.BUTTON
        }
    }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0 && !isCancelled) {
            delay(1000L)
            secondsLeft--
        }
        // Countdown finished and not cancelled — fire SOS
        if (!isCancelled) {
            android.util.Log.d("SosCountdownScreen", "🚨 Countdown complete — firing SOSEngine.triggerSOS($triggerSource)")
            SOSEngine.triggerSOS(triggerSource, context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {
        // Background gradient (kept behind everything)
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

        // Top Nav stays fixed
        TopNavBar(onBack = onBack)

        // Scrollable area containing heading, timer and the status cards — scrolls together
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 25.dp, end = 25.dp, top = 88.dp, bottom = 96.dp), // tightened bottom padding so scrolling stops when status is above slide
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emergency status sits at top of the scroll area
            // ── "SOS TRIGGERED" heading + trigger reason ──────────────────────
            // Only show the emergency header if the countdown wasn't cancelled
            if (!isCancelled && secondsLeft > 0) {
                EmergencyStatus(
                    triggerSource = triggerSource,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Countdown ring — large element that will scroll away when user scrolls down
            Box(modifier = Modifier.fillMaxWidth().height(395.dp), contentAlignment = Alignment.Center) {
                if (!isCancelled) {
                    CountdownRingSection(
                        secondsLeft  = secondsLeft,
                        totalSeconds = totalSeconds,
                        modifier     = Modifier.fillMaxSize()
                    )
                } else {
                    // Show a friendly cancelled UI in the same space as the timer
                    CancelledCard(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status cards are shown only when the countdown reached zero
            if (secondsLeft == 0) {
                StatusCardsPanel(
                    context = context,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Removed extra bottom spacer to prevent over-scrolling — scroll ends when status cards reach the slide
        }

        // Fixed Slide-to-cancel button anchored at bottom above system navigation
        // Hide the slide pill once cancelled or when the countdown reaches zero
        if (!isCancelled && secondsLeft > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 25.dp, end = 25.dp, bottom = 16.dp)
                    .height(88.dp) // tighten height so it matches slide + small padding
                    .zIndex(20f) // raise z-index so it absolutely sits above scroll content
                    .clip(RoundedCornerShape(40.dp))
                    .background(BgBlack)
                    .padding(4.dp), // smaller inner padding so slide fits exactly inside
                contentAlignment = Alignment.Center
            ) {
                SlideToCancelButton(
                    onCancelled = {
                        // Stop the local countdown and show the cancelled UI; parent LaunchedEffect will call external onCancelled after a delay
                        isCancelled = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
        }

         // When cancelled, wait a short moment to display the CancelledCard, then call parent onCancelled()
         LaunchedEffect(isCancelled) {
             if (isCancelled) {
                 // keep the cancelled UI visible briefly before navigating away
                 delay(900L)
                 onCancelled()
             }
         }
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

        // "Nirbhay Live" with pulsing red dot
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
                text          = "UYIRKAVAL Live",
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
fun EmergencyStatus(
    triggerSource: TriggerSource = TriggerSource.BUTTON,
    modifier: Modifier = Modifier
) {
    // Map TriggerSource to a human-readable label
    val reasonLabel = when (triggerSource) {
        TriggerSource.BUTTON -> "Manual SOS Button"
        TriggerSource.SCREAM -> "Scream Detected"
        TriggerSource.FALL   -> "Fall Detected"
        TriggerSource.SHAKE  -> "Shake Detected"
        TriggerSource.VOLUME -> "Volume Button Trigger"
        TriggerSource.POWER  -> "Power Button Trigger"
        TriggerSource.AUTO   -> "AI Auto-Trigger"
    }

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
                text       = reasonLabel,
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
fun StatusCardsPanel(context: Context, modifier: Modifier = Modifier) {
    // ── Real GPS coordinates ────────────────────────────────
    var gpsText by remember { mutableStateOf("Acquiring...") }
    var gpsLocked by remember { mutableStateOf(false) }

    // ── Real connectivity state ─────────────────────────────
    var isOnline by remember { mutableStateOf(false) }

    // ── Audio recording state from SOSEngine ────────────────
    val audioState by SOSEngine.audioState.collectAsState()

    // Poll both every 2 seconds so the UI stays live
    LaunchedEffect(Unit) {
        while (true) {
            // GPS
            val coords = LocationHelper.getLatLng()
            if (coords != null) {
                gpsText = String.format(java.util.Locale.US, "%.4f°N, %.4f°E", coords.first, coords.second)
                gpsLocked = true
            } else {
                gpsText = "Acquiring..."
                gpsLocked = false
            }
            // Connectivity
            isOnline = ConnectivityHelper.isOnline(context)

            delay(2000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .border(1.dp, SliderBorder, RoundedCornerShape(24.dp))
            .padding(17.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {

            // Row 1: Audio capture — dynamically reflects recording state
            val audioLabel = when (audioState) {
                SOSEngine.AudioState.IDLE      -> "Preparing Audio..."
                SOSEngine.AudioState.RECORDING -> "Capturing Audio..."
                SOSEngine.AudioState.SAVED     -> "Audio Saved Locally ✓"
                SOSEngine.AudioState.UPLOADED  -> "Audio Sent to Server ✓"
                SOSEngine.AudioState.FAILED    -> "Audio Capture Failed"
            }
            val audioDotColor = when (audioState) {
                SOSEngine.AudioState.UPLOADED  -> GreenDot
                SOSEngine.AudioState.SAVED     -> AmberDot
                SOSEngine.AudioState.FAILED    -> Color(0xFFEF4444)
                else                           -> AccentRed
            }
            StatusRow(
                dotColor  = audioDotColor,
                iconUrl   = MIC_ICON_URL,
                iconSize  = 11.dp to 14.dp,
                label     = audioLabel,
                trailing  = {
                    if (audioState == SOSEngine.AudioState.RECORDING) {
                        AudioBarsIndicator()
                    } else if (audioState == SOSEngine.AudioState.UPLOADED || audioState == SOSEngine.AudioState.SAVED) {
                        Text(
                            text       = "✓",
                            color      = if (audioState == SOSEngine.AudioState.UPLOADED) GreenDot else AmberDot,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )

            // Row 2: GPS Location — real coordinates
            StatusRow(
                dotColor = if (gpsLocked) GreenDot else AmberDot,
                iconUrl  = GPS_ICON_URL,
                iconSize = 16.dp to 16.dp,
                label    = if (gpsLocked) "GPS Location Locked" else "GPS Acquiring...",
                trailing = {
                    Text(
                        text       = gpsText,
                        color      = TextDim,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            )

            // Row 3: Network status — Online (green) or Mesh fallback (amber)
            if (isOnline) {
                StatusRow(
                    dotColor = GreenDot,
                    iconUrl  = MESH_ICON_URL,
                    iconSize = 18.dp to 17.dp,
                    label    = "Online — Connected",
                    trailing = {
                        Text(
                            text       = "✓",
                            color      = GreenDot,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            } else {
                StatusRow(
                    dotColor = AmberDot,
                    iconUrl  = MESH_ICON_URL,
                    iconSize = 18.dp to 17.dp,
                    label    = "Mesh Network Active",
                    trailing = {
                        // Spinning refresh icon to indicate mesh relay
                        val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                            initialValue  = 0f,
                            targetValue   = 360f,
                            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                            label         = "spinAngle"
                        )
                        AsyncImage(
                            model              = SPINNER_ICON_URL,
                            contentDescription = "Mesh",
                            modifier           = Modifier
                                .size(9.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }
                )
            }
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
    // Track the thumb drag offset using an Animatable for smooth programmatic animations
    val trackWidth   = 326.dp
    val thumbSize    = 64.dp
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { (trackWidth - thumbSize - 16.dp).toPx() }

    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(0f) }

    // Smooth animations when animating to target
    val animationSpecSnap = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val animationSpecToEnd = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)

    // Compute filled width (in dp) accounting for left padding so fill covers the thumb left edge
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val thumbStartPaddingPx = with(density) { 9.dp.toPx() }
    val fillWidthDp by remember { derivedStateOf {
        with(density) { (animatable.value + thumbStartPaddingPx + thumbSizePx / 2f).coerceAtLeast(0f).toDp() }
    } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .alpha(0.88f) // make the entire control slightly translucent
            .clip(CircleShape)
            .background(SliderBg)
            .border(1.dp, SliderBorder, CircleShape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (animatable.value >= maxOffsetPx * 0.85f) {
                                // animate to the end smoothly, then trigger cancellation
                                animatable.animateTo(maxOffsetPx, animationSpec = animationSpecToEnd)
                                onCancelled()
                            } else {
                                // snap back smoothly
                                animatable.animateTo(0f, animationSpec = animationSpecSnap)
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        // update position immediately (clamped) by snapping in a coroutine
                        scope.launch {
                            val next = (animatable.value + dragAmount).coerceIn(0f, maxOffsetPx)
                            animatable.snapTo(next)
                        }
                    }
                )
            }
    ) {
        // Filled portion (left side) — dark, matches track but slightly stronger so it doesn't look white
        Box(
            modifier = Modifier
                .height(80.dp)
                .width(fillWidthDp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(SliderFill)
        )

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
                .offset { IntOffset(animatable.value.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(TextWhite)
                .border(1.dp, SliderBorder, CircleShape), // subtle border instead of shadow
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

// ─────────────────────────────────────────────────────────────────────────────
// Cancelled Card  — shown instead of timer when countdown is cancelled
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CancelledCard(modifier: Modifier = Modifier) {
    // Appear animation
    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(400), label = "cancelFade")

    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RingRed.copy(alpha = 0.18f),
                        Color(0xFF3A0E0E),
                        BgBlack
                    )
                )
            )
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
            .padding(28.dp)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Check circle
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16A34A)), // green
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "SOS Cancelled",
                color = Color(0xFFFFFFFF),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your alert has been cancelled and will not be sent.",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
