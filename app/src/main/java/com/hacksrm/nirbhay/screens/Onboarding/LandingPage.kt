package com.hacksrm.nirbhay.screens.Onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity

// ─────────────────────────────────────────────
//  Color tokens (from Figma)
// ─────────────────────────────────────────────
private val BgDeep = Color(0xFF120808)
private val Red = Color(0xFFEC1313)
private val RedAlpha20 = Color(0x33EC1313)
private val RedAlpha10 = Color(0x1AEC1313)
private val RedAlpha05 = Color(0x0DEC1313)
private val RedAlpha30 = Color(0x4DEC1313)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)
private val TextSubtle = Color(0xFF64748B)

// ─────────────────────────────────────────────
//  Landing Page
// ─────────────────────────────────────────────
@Composable
fun LandingPage(
    onGetStarted: () -> Unit = {},
    onLogIn: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // ── Decorative blobs ──────────────────────────────────
        // Top-left red blob
        Box(
            modifier = Modifier
                .offset(x = (-39).dp, y = (-89).dp)
                .size(width = 156.dp, height = 357.dp)
                .blur(60.dp)
                .background(RedAlpha20, shape = CircleShape)
        )
        // Bottom-right red blob
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 45.dp)
                .size(width = 117.dp, height = 268.dp)
                .blur(50.dp)
                .background(RedAlpha10, shape = CircleShape)
        )

        // ── Main column ───────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar
            TopAppBar(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.weight(1f))

            // Hero visual
            HeroVisual()

            Spacer(Modifier.height(32.dp))

            // Title + subtitle + dots
            HeroCopy()

            Spacer(Modifier.height(24.dp))

            // CTA Buttons
            ActionButtons(
                onGetStarted = onGetStarted,
                onLogIn = onLogIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.weight(1f))

            // Footer
            FooterInfo(modifier = Modifier.fillMaxWidth())
        }
    }
}

// ─────────────────────────────────────────────
//  Top App Bar
// ─────────────────────────────────────────────
@Composable
private fun TopAppBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Shield icon container
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 38.dp)
                    .background(
                        color = RedAlpha20,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = RedAlpha30,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Replace with actual vector resource: ic_shield
                ShieldIcon(tint = Red, modifier = Modifier.size(16.dp, 20.dp))
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = "UYIRKAVAL",
                color = TextWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        // Info button
        IconButton(onClick = {}) {
            InfoIcon(tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────
//  Hero Visual  (glowing circle + fingerprint)
// ─────────────────────────────────────────────
@Composable
private fun HeroVisual() {
    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier.size(256.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer animated glow
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(8.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Red.copy(alpha = glowAlpha * 2),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Main circle
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2A0A0A), BgDeep),
                        center = Offset(0.5f, 0.4f)
                    )
                )
                .border(
                    width = 4.dp,
                    color = RedAlpha20,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Fingerprint arcs drawn via Canvas
            FingerprintIcon(
                modifier = Modifier.size(90.dp),
                color = TextWhite
            )

            // Bottom fade gradient
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, BgDeep),
                            startY = 0.4f * 256f,
                            endY = 256f * 1.5f
                        )
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Hero copy: title, subtitle, indicator dots
// ─────────────────────────────────────────────
@Composable
private fun HeroCopy() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Text(
            text = "UYIRKAVAL",
            color = TextWhite,
            fontSize = 60.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-3).sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Your Silent Guardian in a Connected World",
            color = TextMuted,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 29.sp
        )

        Spacer(Modifier.height(16.dp))

        // Dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(Red, RoundedCornerShape(50))
            )
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 4.dp)
                    .background(RedAlpha30, RoundedCornerShape(50))
            )
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 4.dp)
                    .background(RedAlpha30, RoundedCornerShape(50))
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Action Buttons
// ─────────────────────────────────────────────
@Composable
private fun ActionButtons(
    onGetStarted: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary – Get Started
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Red)
                .clickable { onGetStarted() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Get Started",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                ArrowRightIcon(tint = TextWhite, modifier = Modifier.size(14.dp))
            }
        }

        // Secondary – Log In
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 2.dp,
                    color = RedAlpha30.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable { onLogIn() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Log In",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Footer Info
// ─────────────────────────────────────────────
@Composable
private fun FooterInfo(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Three badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterBadge(
                icon = { ShieldIcon(tint = Red, modifier = Modifier.size(16.dp, 20.dp)) },
                label = "ENCRYPTED"
            )
            FooterBadge(
                icon = { ClockIcon(tint = Red, modifier = Modifier.size(16.dp, 20.dp)) },
                label = "REAL-TIME"
            )
            FooterBadge(
                icon = { BoltIcon(tint = Red, modifier = Modifier.size(17.dp, 18.dp)) },
                label = "24/7 SOS"
            )
        }

        // Copyright
        Text(
            text = "© UyirKaval - Built by Team kernel",
            color = TextSubtle,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun FooterBadge(
    icon: @Composable () -> Unit,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = label,
            color = TextSubtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ─────────────────────────────────────────────
//  Inline icon composables (Canvas-drawn)
//  Replace these with your actual vector assets
//  via painterResource(R.drawable.ic_xxx) once
//  you add them to res/drawable.
// ─────────────────────────────────────────────

@Composable
private fun ShieldIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h * 0.175f)
            lineTo(w, h * 0.45f)
            cubicTo(w, h * 0.7f, w * 0.65f, h * 0.885f, w / 2f, h)
            cubicTo(w * 0.35f, h * 0.885f, 0f, h * 0.7f, 0f, h * 0.45f)
            lineTo(0f, h * 0.175f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.25f))
        drawPath(path, color = tint, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun InfoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 1.dp.toPx()
        drawCircle(color = tint, radius = r, style = Stroke(width = 1.5.dp.toPx()))
        drawLine(
            tint,
            Offset(cx, cy - r * 0.3f),
            Offset(cx, cy + r * 0.3f),
            strokeWidth = 1.5.dp.toPx()
        )
        drawCircle(tint, radius = 1.5.dp.toPx(), center = Offset(cx, cy - r * 0.5f))
    }
}

@Composable
private fun ArrowRightIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(tint, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 2.dp.toPx())
        drawPath(Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h / 2f)
            lineTo(w * 0.5f, h)
        }, color = tint, style = stroke)
    }
}

@Composable
private fun ClockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 1.dp.toPx()
        drawCircle(color = tint, radius = r, style = Stroke(width = 1.5.dp.toPx()))
        drawLine(tint, Offset(cx, cy), Offset(cx, cy - r * 0.5f), strokeWidth = 1.5.dp.toPx())
        drawLine(
            tint,
            Offset(cx, cy),
            Offset(cx + r * 0.35f, cy + r * 0.2f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
private fun BoltIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.6f, 0f)
            lineTo(w * 0.1f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.4f, h)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.5f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
private fun FingerprintIcon(modifier: Modifier = Modifier, color: Color = TextWhite) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeW = 2.5.dp.toPx()

        // Draw concentric arc rings simulating a fingerprint
        val radii = listOf(
            size.minDimension * 0.47f to color.copy(alpha = 0.3f),
            size.minDimension * 0.39f to color.copy(alpha = 0.5f),
            size.minDimension * 0.31f to color.copy(alpha = 0.7f),
            size.minDimension * 0.23f to color.copy(alpha = 0.85f),
            size.minDimension * 0.13f to color.copy(alpha = 1.0f),
        )

        radii.forEach { (r, c) ->
            drawArc(
                color = c,
                startAngle = 200f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────
@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 892,
    name = "Landing Page"
)
@Composable
fun LandingPagePreview() {
    LandingPage()
}