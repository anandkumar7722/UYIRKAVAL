package com.hacksrm.nirbhay.screens.Stealth_Dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ─────────────────────────────────────────────────────────────────────────────
//  Color tokens (matches Figma)
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF221010)
private val BgDeeper      = Color(0xFF120808)
private val Red           = Color(0xFFEC1313)
private val RedAlpha05    = Color(0x0DEC1313)
private val RedAlpha10    = Color(0x1AEC1313)
private val RedAlpha20    = Color(0x33EC1313)
private val RedAlpha30    = Color(0x4DEC1313)
private val TextWhite     = Color(0xFFF1F5F9)
private val TextMuted     = Color(0xFF94A3B8)
private val TextSubtle    = Color(0xFF64748B)
private val SlateCard     = Color(0xFF1E293B)
private val SlateBorder   = Color(0xFF334155)

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────
enum class GuardianGender { MALE, FEMALE, UNSET }

data class Guardian(
    val id: Int,
    val name: String = "",
    val relationship: String = "",
    val gender: GuardianGender = GuardianGender.UNSET
) {
    val isFilled: Boolean get() = name.isNotBlank()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Emergency Contact Page
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyContactPage(
    onBack: () -> Unit = {},
    onAddGuardian: () -> Unit = {},
    onImport: () -> Unit = {},
    onContinue: () -> Unit = {}
) {
    // Sample data – two filled guardians, three empty slots
    var guardians by remember {
        mutableStateOf(
            listOf(
                Guardian(1, "Sarah Connor",  "Mother", GuardianGender.FEMALE),
                Guardian(2, "John Connor",   "Brother", GuardianGender.MALE),
                Guardian(3),
                Guardian(4),
                Guardian(5)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ── Background decorative blobs ───────────────────────────────────
        DecorativeBlobs()

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────
            ECHeader(onBack = onBack)

            // ── Scrollable body ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Title section
                TitleSection()

                Spacer(Modifier.height(40.dp))

                // Guardian grid  (2 + 2 + 1 centre)
                GuardianGrid(
                    guardians = guardians,
                    onAddGuardian = onAddGuardian
                )

                Spacer(Modifier.height(40.dp))

                // Call-to-action buttons
                CTAButtons(
                    onAddGuardian = onAddGuardian,
                    onImport = onImport
                )

                Spacer(Modifier.height(24.dp))

                // Security info card
                SecurityInfoCard()

                Spacer(Modifier.height(120.dp))
            }
        }

        // ── Sticky bottom bar (Continue) ──────────────────────────────────
        BottomContinueBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onContinue = onContinue
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Decorative background blobs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DecorativeBlobs() {
    // top-right blob
    Box(
        modifier = Modifier
            .offset(x = 134.dp, y = 0.dp)
            .size(256.dp)
            .blur(50.dp)
            .background(RedAlpha05, CircleShape)
    )
    // bottom-left blob
    Box(
        modifier = Modifier
            .offset(x = 0.dp, y = 917.dp)
            .size(256.dp)
            .blur(60.dp)
            .background(RedAlpha10, CircleShape)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ECHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDark.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(RedAlpha10)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            ChevronLeftIcon(tint = TextWhite, modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = "Emergency Contacts",
            color = TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.27).sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Spacer to balance back button
        Spacer(Modifier.width(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Title section
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TitleSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Heading with red accent bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(Red, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Your Guardian\nCircle",
                color = TextWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                lineHeight = 38.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "People who will be notified instantly in case of an emergency.",
            color = TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(8.dp))

        // Sub-note with icon
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Red, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Add up to 5 guardians. Alerts are sent in real-time.",
                color = TextSubtle,
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Guardian grid  — row of 2, row of 2, then 1 centred
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuardianGrid(
    guardians: List<Guardian>,
    onAddGuardian: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: guardians 0 & 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GuardianSlot(guardian = guardians[0], onAdd = onAddGuardian)
            GuardianSlot(guardian = guardians[1], onAdd = onAddGuardian)
        }

        // Row 2: guardians 2 & 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GuardianSlot(guardian = guardians[2], onAdd = onAddGuardian)
            GuardianSlot(guardian = guardians[3], onAdd = onAddGuardian)
        }

        // Row 3: guardian 4 centred
        GuardianSlot(guardian = guardians[4], onAdd = onAddGuardian)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single guardian slot
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuardianSlot(
    guardian: Guardian,
    onAdd: () -> Unit
) {
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(guardian.isFilled) {
        enterAnim.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        if (guardian.isFilled) {
            FilledGuardianAvatar(guardian = guardian)
        } else {
            EmptyGuardianSlot(onAdd = onAdd)
        }

        Spacer(Modifier.height(12.dp))

        // Name
        Text(
            text = if (guardian.isFilled) guardian.name else "Add Guardian",
            color = if (guardian.isFilled) TextWhite else TextSubtle,
            fontSize = 13.sp,
            fontWeight = if (guardian.isFilled) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )

        // Relationship
        if (guardian.isFilled) {
            Text(
                text = guardian.relationship,
                color = Red.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Filled avatar — female gets her avatar, male gets the Figma vector style
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FilledGuardianAvatar(guardian: Guardian) {
    Box(modifier = Modifier.size(96.dp)) {
        // Outer glowing ring
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(Red, RedAlpha20, Red)
                    ),
                    shape = CircleShape
                )
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = Red.copy(alpha = 0.4f),
                    spotColor = Red.copy(alpha = 0.4f)
                )
        )

        // Avatar circle background
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(6.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = when (guardian.gender) {
                            GuardianGender.FEMALE -> listOf(Color(0xFF4A1A3A), Color(0xFF2D0A20))
                            GuardianGender.MALE   -> listOf(Color(0xFF1A2A4A), Color(0xFF0A1020))
                            GuardianGender.UNSET  -> listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            when (guardian.gender) {
                GuardianGender.FEMALE -> FemaleAvatarCanvas(modifier = Modifier.size(56.dp))
                GuardianGender.MALE   -> MaleAvatarCanvas(modifier = Modifier.size(56.dp))
                GuardianGender.UNSET  -> GenericAvatarCanvas(modifier = Modifier.size(56.dp))
            }
        }

        // Green "verified" badge (top-right corner — matches Figma checkmark badge)
        Box(
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(listOf(Color(0xFF16A34A), Color(0xFF14532D)))
                )
                .border(2.dp, BgDark, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CheckmarkIcon(tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty slot button (matches Figma "+" circle)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyGuardianSlot(onAdd: () -> Unit) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseAnim.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(CircleShape)
            .background(RedAlpha05)
            .border(
                width = 1.5.dp,
                color = RedAlpha30,
                shape = CircleShape
            )
            .clickable { onAdd() },
        contentAlignment = Alignment.Center
    ) {
        // Canvas-drawn "+" icon (matches Figma vector)
        Canvas(modifier = Modifier.size(27.5.dp, 20.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arm = size.minDimension * 0.42f
            val sw  = 2.5.dp.toPx()
            drawLine(Red, Offset(cx - arm, cy), Offset(cx + arm, cy), sw, StrokeCap.Round)
            drawLine(Red, Offset(cx, cy - arm), Offset(cx, cy + arm), sw, StrokeCap.Round)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CTA Buttons
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CTAButtons(
    onAddGuardian: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Primary – Add Guardian
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Red)
                .clickable { onAddGuardian() },
            contentAlignment = Alignment.Center
        ) {
            // Red shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Red.copy(alpha = 0.4f),
                        spotColor   = Red.copy(alpha = 0.4f)
                    )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Person + add icon
                PersonAddIcon(tint = Color.White, modifier = Modifier.size(22.dp, 16.dp))
                Text(
                    text = "Add a Guardian",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Secondary – Import from contacts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, RedAlpha30, RoundedCornerShape(20.dp))
                .background(RedAlpha05)
                .clickable { onImport() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ImportContactIcon(tint = Red, modifier = Modifier.size(22.dp, 16.dp))
                Text(
                    text = "Import from Contacts",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Security info card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SecurityInfoCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SlateCard.copy(alpha = 0.5f))
            .border(1.dp, SlateBorder, RoundedCornerShape(20.dp))
            .padding(17.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LockIcon(tint = TextMuted, modifier = Modifier.size(16.dp, 20.dp))

        Text(
            text = "Your guardians' data is end-to-end encrypted. " +
                    "They will only receive alerts when you trigger an SOS. " +
                    "You can remove or update guardians anytime from Settings.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sticky bottom "Continue" bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BottomContinueBar(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, BgDark, BgDark),
                    startY = 0f,
                    endY   = 300f
                )
            )
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Red)
                    .clickable { onContinue() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Continue",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ArrowRightSmallIcon(tint = Color.White, modifier = Modifier.size(8.dp, 12.dp))
                }
            }

            Text(
                text = "You can always update guardians later in Settings",
                color = TextSubtle,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Canvas-drawn Avatars
// ─────────────────────────────────────────────────────────────────────────────

/** Female avatar — long hair, feminine silhouette (rose-toned) */
@Composable
private fun FemaleAvatarCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Hair (long, behind everything)
        val hairColor = Color(0xFF8B3A6B)
        val hairPath = Path().apply {
            moveTo(cx - w * 0.28f, h * 0.28f)
            cubicTo(cx - w * 0.38f, h * 0.1f, cx - w * 0.25f, -h * 0.05f, cx, -h * 0.02f)
            cubicTo(cx + w * 0.25f, -h * 0.05f, cx + w * 0.38f, h * 0.1f, cx + w * 0.28f, h * 0.28f)
            // down sides past shoulder
            cubicTo(cx + w * 0.42f, h * 0.58f, cx + w * 0.38f, h * 0.85f, cx + w * 0.3f, h)
            lineTo(cx - w * 0.3f, h)
            cubicTo(cx - w * 0.38f, h * 0.85f, cx - w * 0.42f, h * 0.58f, cx - w * 0.28f, h * 0.28f)
            close()
        }
        drawPath(hairPath, hairColor)

        // Neck
        drawRect(
            color = Color(0xFFF4C2D0),
            topLeft = Offset(cx - w * 0.09f, h * 0.46f),
            size    = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.14f)
        )

        // Face
        val skinF = Color(0xFFF4C2D0)
        drawOval(
            color    = skinF,
            topLeft  = Offset(cx - w * 0.23f, h * 0.06f),
            size     = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.42f)
        )

        // Eyes
        val eyeY = h * 0.23f
        drawOval(Color(0xFF2D1B2E), Offset(cx - w * 0.12f, eyeY), androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.05f))
        drawOval(Color(0xFF2D1B2E), Offset(cx + w * 0.05f, eyeY), androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.05f))
        // Pupils (shimmer)
        drawCircle(Color.White, radius = w * 0.012f, center = Offset(cx - w * 0.10f, eyeY + h * 0.008f))
        drawCircle(Color.White, radius = w * 0.012f, center = Offset(cx + w * 0.07f, eyeY + h * 0.008f))

        // Smile
        val smilePath = Path().apply {
            moveTo(cx - w * 0.08f, h * 0.35f)
            quadraticBezierTo(cx, h * 0.41f, cx + w * 0.08f, h * 0.35f)
        }
        drawPath(smilePath, Color(0xFFE8789A), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Body / shoulders
        val bodyPath = Path().apply {
            moveTo(cx - w * 0.38f, h)
            cubicTo(cx - w * 0.38f, h * 0.74f, cx - w * 0.18f, h * 0.62f, cx, h * 0.62f)
            cubicTo(cx + w * 0.18f, h * 0.62f, cx + w * 0.38f, h * 0.74f, cx + w * 0.38f, h)
            close()
        }
        drawPath(bodyPath, Color(0xFFAD3370))
    }
}

/** Male avatar — short hair, masculine silhouette (navy/blue-toned) */
@Composable
private fun MaleAvatarCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Short hair
        val hairPath = Path().apply {
            moveTo(cx - w * 0.24f, h * 0.22f)
            cubicTo(cx - w * 0.28f, h * 0.04f, cx - w * 0.18f, -h * 0.04f, cx, -h * 0.02f)
            cubicTo(cx + w * 0.18f, -h * 0.04f, cx + w * 0.28f, h * 0.04f, cx + w * 0.24f, h * 0.22f)
            cubicTo(cx + w * 0.22f, h * 0.08f, cx, h * 0.04f, cx - w * 0.22f, h * 0.08f)
            close()
        }
        drawPath(hairPath, Color(0xFF3D2B1F))

        // Neck
        drawRect(
            color   = Color(0xFFD4A574),
            topLeft = Offset(cx - w * 0.1f, h * 0.46f),
            size    = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.14f)
        )

        // Face
        drawOval(
            color   = Color(0xFFD4A574),
            topLeft = Offset(cx - w * 0.23f, h * 0.08f),
            size    = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.40f)
        )

        // Eyes
        val eyeY = h * 0.24f
        drawOval(Color(0xFF1A1A2E), Offset(cx - w * 0.12f, eyeY), androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.05f))
        drawOval(Color(0xFF1A1A2E), Offset(cx + w * 0.05f, eyeY), androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.05f))
        drawCircle(Color.White, radius = w * 0.012f, center = Offset(cx - w * 0.10f, eyeY + h * 0.01f))
        drawCircle(Color.White, radius = w * 0.012f, center = Offset(cx + w * 0.07f, eyeY + h * 0.01f))

        // Brows (masculine, thicker)
        drawLine(Color(0xFF3D2B1F), Offset(cx - w * 0.14f, eyeY - h * 0.05f), Offset(cx - w * 0.05f, eyeY - h * 0.055f), strokeWidth = 2.2.dp.toPx())
        drawLine(Color(0xFF3D2B1F), Offset(cx + w * 0.04f, eyeY - h * 0.055f), Offset(cx + w * 0.13f, eyeY - h * 0.05f), strokeWidth = 2.2.dp.toPx())

        // Smile (subtle)
        val smilePath = Path().apply {
            moveTo(cx - w * 0.08f, h * 0.36f)
            quadraticBezierTo(cx, h * 0.40f, cx + w * 0.08f, h * 0.36f)
        }
        drawPath(smilePath, Color(0xFFB07040), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Body (shirt)
        val bodyPath = Path().apply {
            moveTo(cx - w * 0.42f, h)
            cubicTo(cx - w * 0.42f, h * 0.72f, cx - w * 0.22f, h * 0.60f, cx, h * 0.60f)
            cubicTo(cx + w * 0.22f, h * 0.60f, cx + w * 0.42f, h * 0.72f, cx + w * 0.42f, h)
            close()
        }
        drawPath(bodyPath, Color(0xFF1E3A5F))
    }
}

/** Generic (unset gender) avatar — neutral grey silhouette */
@Composable
private fun GenericAvatarCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Head
        drawOval(
            color   = Color(0xFF475569),
            topLeft = Offset(cx - w * 0.22f, h * 0.04f),
            size    = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.38f)
        )

        // Body
        val bodyPath = Path().apply {
            moveTo(cx - w * 0.4f, h)
            cubicTo(cx - w * 0.4f, h * 0.70f, cx - w * 0.2f, h * 0.58f, cx, h * 0.58f)
            cubicTo(cx + w * 0.2f, h * 0.58f, cx + w * 0.4f, h * 0.70f, cx + w * 0.4f, h)
            close()
        }
        drawPath(bodyPath, Color(0xFF334155))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Small icon composables (Canvas-drawn — swap for vector resources)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChevronLeftIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.65f, 0f)
            lineTo(size.width * 0.15f, size.height * 0.5f)
            lineTo(size.width * 0.65f, size.height)
        }
        drawPath(path, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun CheckmarkIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.5f)
            lineTo(size.width * 0.4f, size.height * 0.8f)
            lineTo(size.width * 0.9f, size.height * 0.2f)
        }
        drawPath(path, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun PersonAddIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        // Head
        drawCircle(tint, radius = h * 0.22f, center = Offset(w * 0.35f, h * 0.28f))
        // Body arc
        drawArc(tint, 180f, 180f, false,
            Offset(w * 0.05f, h * 0.52f),
            androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.5f),
            style = Stroke(2.dp.toPx()))
        // "+" for add
        val cx2 = w * 0.82f; val cy2 = h * 0.52f; val arm = h * 0.2f
        drawLine(tint, Offset(cx2 - arm, cy2), Offset(cx2 + arm, cy2), 2.dp.toPx())
        drawLine(tint, Offset(cx2, cy2 - arm), Offset(cx2, cy2 + arm), 2.dp.toPx())
    }
}

@Composable
private fun ImportContactIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawCircle(tint, radius = h * 0.22f, center = Offset(w * 0.38f, h * 0.28f))
        drawArc(tint, 180f, 180f, false,
            Offset(w * 0.05f, h * 0.52f),
            androidx.compose.ui.geometry.Size(w * 0.66f, h * 0.5f),
            style = Stroke(2.dp.toPx()))
        // Arrow
        drawLine(tint, Offset(w * 0.75f, h * 0.2f), Offset(w * 0.95f, h * 0.4f), 2.dp.toPx())
        drawLine(tint, Offset(w * 0.75f, h * 0.6f), Offset(w * 0.95f, h * 0.4f), 2.dp.toPx())
    }
}

@Composable
private fun LockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val body = RoundedCornerShape(4.dp)
        drawRoundRect(tint, Offset(w * 0.1f, h * 0.44f),
            androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.56f),
            androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = Stroke(2.dp.toPx()))
        drawArc(tint, 180f, 180f, false,
            Offset(w * 0.25f, h * 0.04f),
            androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.44f),
            style = Stroke(2.dp.toPx()))
        drawCircle(tint, radius = 2.5.dp.toPx(), center = Offset(w * 0.5f, h * 0.68f))
    }
}

@Composable
private fun ArrowRightSmallIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.5f)
            lineTo(size.width, size.height * 0.5f)
            moveTo(size.width * 0.4f, 0f)
            lineTo(size.width, size.height * 0.5f)
            lineTo(size.width * 0.4f, size.height)
        }
        drawPath(path, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Ease helpers (available in Compose 1.4+, fallback provided)
private val EaseInOutSine: Easing = Easing { fraction ->
    (-(kotlin.math.cos(Math.PI * fraction) - 1) / 2).toFloat()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────
@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 1223,
    name = "Emergency Contact Page"
)
@Composable
fun EmergencyContactPagePreview() {
    EmergencyContactPage()
}