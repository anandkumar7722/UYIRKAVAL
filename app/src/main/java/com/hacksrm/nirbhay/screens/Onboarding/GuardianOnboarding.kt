package com.hacksrm.nirbhay.screens.Onboarding


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ─────────────────────────────────────────────────────────────────────────────
//  Color tokens (Figma exact)
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF221010)
private val BgInput = Color(0x80221010)   // rgba(34,16,16,0.5)
private val Red = Color(0xFFEC1313)
private val RedAlpha05 = Color(0x0DEC1313)
private val RedAlpha10 = Color(0x1AEC1313)
private val RedAlpha20 = Color(0x33EC1313)
private val RedAlpha30 = Color(0x4DEC1313)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)
private val TextSubtle = Color(0xFF64748B)
private val TextLabel = Color(0xFFCBD5E1)
private val TextPlaceholder = Color(0xFF475569)

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────
data class GuardianFormData(
    val fullName: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
)

private val relationshipOptions = listOf(
    "Mother", "Father", "Sister", "Brother",
    "Spouse", "Partner", "Friend", "Colleague", "Other"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Add Trusted Contacts Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AddTrustedContactsPage(
    currentStep: Int = 1,     // 1-based, used for progress bar
    totalSteps: Int = 3,
    guardianNumber: Int = 1,     // which guardian slot we're filling
    totalGuardians: Int = 5,
    filledGuardians: Int = 0,     // how many already saved
    onBack: () -> Unit = {},
    onSaveAndContinue: (GuardianFormData) -> Unit = {},
    onAddAnother: () -> Unit = {},
) {
    var formData by remember { mutableStateOf(GuardianFormData()) }
    var showRelationshipSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) {
                focusManager.clearFocus()
            }
    ) {
        // ── Background decorative blobs ───────────────────────────────────
        DecorBlobs()

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Sticky header ─────────────────────────────────────────────
            ATCHeader(onBack = onBack)

            // ── Scrollable body ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {

                // Progress bar
                ProgressSection(
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    filledCount = filledGuardians,
                    totalSlots = totalGuardians
                )

                // Section header
                SectionHeader()

                Spacer(Modifier.height(8.dp))

                // Form area
                FormArea(
                    guardianNumber = guardianNumber,
                    totalGuardians = totalGuardians,
                    filledGuardians = filledGuardians,
                    formData = formData,
                    onFormChange = { formData = it },
                    onRelationshipClick = { showRelationshipSheet = true },
                    onAddAnother = onAddAnother
                )

                // Space for bottom bar
                Spacer(Modifier.height(140.dp))
            }
        }

        // ── Sticky bottom action bar ──────────────────────────────────────
        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onSaveAndContinue = { onSaveAndContinue(formData) }
        )

        // ── Relationship bottom sheet ─────────────────────────────────────
        if (showRelationshipSheet) {
            RelationshipBottomSheet(
                selected = formData.relationship,
                onSelect = {
                    formData = formData.copy(relationship = it)
                    showRelationshipSheet = false
                },
                onDismiss = { showRelationshipSheet = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Decorative background blobs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DecorBlobs() {
    // Top-right blob
    Box(
        modifier = Modifier
            .offset(x = 134.dp, y = 0.dp)
            .size(256.dp)
            .blur(50.dp)
            .background(RedAlpha10, CircleShape)
    )
    // Bottom-left blob
    Box(
        modifier = Modifier
            .offset(x = 0.dp, y = 508.dp)
            .size(384.dp)
            .blur(60.dp)
            .background(RedAlpha05, CircleShape)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Header — back button + "Add Guardians" title
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ATCHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDark.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(RedAlpha10)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            ChevronLeftIcon(
                tint = TextWhite,
                modifier = Modifier.size(16.dp)
            )
        }

        // Centred title (padded right to visually center against the 48dp back btn)
        Text(
            text = "Add Guardians",
            color = TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.27).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Progress bar section
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ProgressSection(
    currentStep: Int,
    totalSteps: Int,
    filledCount: Int,
    totalSlots: Int,
) {
    val progress = currentStep.toFloat() / totalSteps.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Onboarding Progress",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Step $currentStep of $totalSteps",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(RedAlpha20)
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(50))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Red, Color(0xFFFF4444))
                        )
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section header — title + subtitle
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Red accent bar + heading
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(Red, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Your Trusted\nContacts",
                color = TextWhite,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                lineHeight = 36.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Add up to 5 guardians who will be notified\ninstantly in case of an emergency.",
            color = TextMuted,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Form area — card + add-another button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FormArea(
    guardianNumber: Int,
    totalGuardians: Int,
    filledGuardians: Int,
    formData: GuardianFormData,
    onFormChange: (GuardianFormData) -> Unit,
    onRelationshipClick: () -> Unit,
    onAddAnother: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Guardian form card
        GuardianFormCard(
            guardianNumber = guardianNumber,
            formData = formData,
            onFormChange = onFormChange,
            onRelationshipClick = onRelationshipClick
        )

        // Add another guardian dashed button
        AddAnotherButton(
            filledCount = filledGuardians,
            totalGuardians = totalGuardians,
            onAddAnother = onAddAnother
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Guardian form card  (Figma: Overlay+Border)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuardianFormCard(
    guardianNumber: Int,
    formData: GuardianFormData,
    onFormChange: (GuardianFormData) -> Unit,
    onRelationshipClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RedAlpha05)
            .border(1.dp, RedAlpha20, RoundedCornerShape(24.dp))
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── "Guardian N" label row ────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PersonAddSmallIcon(tint = Red, modifier = Modifier.size(22.dp, 16.dp))
            Text(
                text = "Guardian $guardianNumber",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Full Name field ───────────────────────────────────────────────
        FormField(
            label = "Full Name",
            value = formData.fullName,
            placeholder = "Sarah Connor",
            onValueChange = { onFormChange(formData.copy(fullName = it)) },
            keyboardType = KeyboardType.Text
        )

        // ── Phone Number field ────────────────────────────────────────────
        FormField(
            label = "Phone Number",
            value = formData.phoneNumber,
            placeholder = "+1 (555) 000-0000",
            onValueChange = { onFormChange(formData.copy(phoneNumber = it)) },
            keyboardType = KeyboardType.Phone
        )

        // ── Relationship dropdown ─────────────────────────────────────────
        RelationshipField(
            selected = formData.relationship,
            onClick = onRelationshipClick
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reusable text input field  (Full Name / Phone)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Red.copy(alpha = 0.6f) else RedAlpha30,
        animationSpec = tween(200),
        label = "borderAnim"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color(0x66221010) else BgInput,
        animationSpec = tween(200),
        label = "bgAnim"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Label
        Text(
            text = label,
            color = TextLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Input box
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 17.dp),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextPlaceholder,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Relationship dropdown field (tappable — opens bottom sheet)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RelationshipField(
    selected: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = "Relationship",
            color = TextLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BgInput)
                .border(1.dp, RedAlpha30, RoundedCornerShape(24.dp))
                .clickable { onClick() }
                .padding(horizontal = 17.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selected.ifBlank { "Select relation" },
                    color = if (selected.isBlank()) TextWhite else TextWhite,
                    fontSize = 16.sp
                )
                ChevronDownIcon(
                    tint = Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  "Add Another Guardian (N/5)" dashed button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AddAnotherButton(
    filledCount: Int,
    totalGuardians: Int,
    onAddAnother: () -> Unit,
) {
    val nextSlot = filledCount + 1

    // Animated dashed border via canvas
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onAddAnother() }
            .drawBehind {
                val strokeW = 2.dp.toPx()
                val dashLen = 10.dp.toPx()
                val gapLen = 6.dp.toPx()
                val r = 24.dp.toPx()
                drawRoundRect(
                    color = Color(0x4DEC1313),
                    style = Stroke(
                        width = strokeW,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen))
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "+" icon
            Canvas(modifier = Modifier.size(14.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arm = size.minDimension * 0.42f
                val sw = 2.dp.toPx()
                drawLine(Red, Offset(cx - arm, cy), Offset(cx + arm, cy), sw, StrokeCap.Round)
                drawLine(Red, Offset(cx, cy - arm), Offset(cx, cy + arm), sw, StrokeCap.Round)
            }

            Text(
                text = "Add Another Guardian ($nextSlot/$totalGuardians)",
                color = Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sticky bottom action bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BottomActionBar(
    onSaveAndContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, BgDark.copy(alpha = 0.95f), BgDark),
                    startY = 0f,
                    endY = 200f
                )
            )
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Save and Continue button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Red.copy(alpha = 0.3f),
                    spotColor = Red.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(Red)
                .clickable { onSaveAndContinue() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Save and Continue",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                // Small chevron right
                Canvas(modifier = Modifier.size(7.4.dp, 12.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        // Hint text
        Text(
            text = "You can update your guardians anytime in settings.",
            color = TextSubtle,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Relationship bottom sheet
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RelationshipBottomSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    )

    // Sheet content aligned to bottom
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF2D1010))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(RedAlpha30, Color.Transparent)),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .padding(top = 12.dp, bottom = 32.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {}
        ) {
            // Handle pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(TextSubtle, RoundedCornerShape(50))
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Select Relationship",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            relationshipOptions.forEach { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .background(
                            if (isSelected) RedAlpha10 else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Red else TextWhite,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        // Checkmark
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.1f, size.height * 0.5f)
                                lineTo(size.width * 0.42f, size.height * 0.82f)
                                lineTo(size.width * 0.9f, size.height * 0.18f)
                            }
                            drawPath(
                                path,
                                Red,
                                style = Stroke(
                                    2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                // Divider
                if (option != relationshipOptions.last()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .padding(horizontal = 24.dp)
                            .background(TextSubtle.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Small inline icon composables  (Canvas-drawn — swap for R.drawable vectors)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChevronLeftIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.65f, 0f)
            lineTo(size.width * 0.15f, size.height * 0.5f)
            lineTo(size.width * 0.65f, size.height)
        }
        drawPath(
            path,
            tint,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun ChevronDownIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.38f)
            lineTo(size.width * 0.5f, size.height * 0.62f)
            lineTo(size.width * 0.8f, size.height * 0.38f)
        }
        drawPath(
            path,
            tint,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun PersonAddSmallIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width;
        val h = size.height
        drawCircle(tint, radius = h * 0.22f, center = Offset(w * 0.32f, h * 0.28f))
        drawArc(
            color = tint,
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.04f, h * 0.52f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.5f),
            style = Stroke(2.dp.toPx())
        )
        val cx = w * 0.80f;
        val cy = h * 0.40f;
        val arm = h * 0.18f
        drawLine(tint, Offset(cx - arm, cy), Offset(cx + arm, cy), 2.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(cx, cy - arm), Offset(cx, cy + arm), 2.dp.toPx(), StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────
@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 892,
    name = "Add Trusted Contacts"
)
@Composable
fun AddTrustedContactsPreview() {
    AddTrustedContactsPage(
        currentStep = 1,
        totalSteps = 1,
        guardianNumber = 1,
        totalGuardians = 5,
        filledGuardians = 0
    )
}