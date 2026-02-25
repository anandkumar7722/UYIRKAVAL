package com.hacksrm.nirbhay.screens.Onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

// ─────────────────────────────────────────────────────────────────────────────
//  Colors (aligned with app-wide tokens)
//  Use same hex alpha values other pages use (LandingPage / GuardianOnboarding / HomeScreen)
// ─────────────────────────────────────────────────────────────────────────────
private val BgBase    = Color(0xFF221010)           // app dark background (matches other screens)
private val BgDark    = BgBase

private val Red       = Color(0xFFEC1313)
private val RedDark   = Color(0xFFB00E0E)
// alpha-encoded variants (consistent with other files)
private val RedAlpha05 = Color(0x0DEC1313)
private val RedAlpha10 = Color(0x1AEC1313)
private val RedAlpha20 = Color(0x33EC1313)
private val RedAlpha30 = Color(0x4DEC1313)

private val CardBg   = Color(0x662D0A0A)  // same as original login/guardian
private val CardBgSolid = Color(0xF2331919)
private val InputBg  = Color(0x80221010)  // rgba(34,16,16,0.5) used elsewhere
private val FocusBg  = Color(0x99221010)  // focused input bg used previously
private val FocusBorder = Color(0xB3EC1313) // focused border (semi-strong red)

private val TextWhite       = Color(0xFFF1F5F9)
private val TextMuted       = Color(0xFF94A3B8)
private val TextSubtle      = Color(0xFF64748B)
private val TextLabel       = Color(0xFFCBD5E1)
private val TextPlaceholder = Color(0xFF475569)
private val ErrorRed        = Color(0xFFEF4444)

private enum class AuthTab { LOGIN, SIGNUP }

// ─────────────────────────────────────────────────────────────────────────────
//  Screen entry point
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginSignupPage(
    onLoginSuccess : () -> Unit = {},
    onSignupSuccess: () -> Unit = {}
) {
    // Tabbed Login / Sign Up UI
    var activeTab by remember { mutableStateOf(AuthTab.LOGIN) }

    // Shared states for both tabs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val isLoginValid = email.isNotBlank() && password.length >= 6
    val isSignupValid = email.isNotBlank() && password.length >= 6 && password == confirmPass

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        // Subtle background accents
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxDim = size.maxDimension
            drawCircle(brush = Brush.radialGradient(listOf(Red.copy(alpha = 0.16f), Color.Transparent)), center = Offset(size.width * 0.15f, size.height * 0.12f), radius = maxDim)
            drawCircle(brush = Brush.radialGradient(listOf(Red.copy(alpha = 0.10f), Color.Transparent)), center = Offset(size.width * 0.84f, size.height * 0.88f), radius = maxDim * 0.9f)
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(60.dp))
            LogoHeader()
            Spacer(modifier = Modifier.height(20.dp))

            // Tabs
            TabRow(activeTab = activeTab, onTabChange = { activeTab = it })
            Spacer(modifier = Modifier.height(12.dp))

            // Content
            when (activeTab) {
                AuthTab.LOGIN -> {
                    LoginTabContent(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        onLoginClick = {
                            if (!isLoginValid) {
                                val msg = when {
                                    email.isBlank() -> "Please enter email"
                                    password.length < 6 -> "Password must be at least 6 characters"
                                    else -> "Please complete the form"
                                }
                                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                                return@LoginTabContent
                            }
                            onLoginSuccess()
                        }
                    )
                }

                AuthTab.SIGNUP -> {
                    SignupTabContent(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        confirmPass = confirmPass,
                        onConfirmChange = { confirmPass = it },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        onSignupClick = {
                            if (!isSignupValid) {
                                val msg = when {
                                    email.isBlank() -> "Please enter email"
                                    password.length < 6 -> "Password must be at least 6 characters"
                                    password != confirmPass -> "Passwords do not match"
                                    else -> "Please complete the form"
                                }
                                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                                return@SignupTabContent
                            }
                            onSignupSuccess()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Logo + tagline
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LogoHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text          = "NIRBHAY",
            color         = TextWhite,
            fontSize      = 36.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = (-1.8).sp,
            textAlign     = TextAlign.Center
        )
        Text(
            text          = "YOUR SHIELD IN THE DARK",
            color         = Red,
            fontSize      = 14.sp,
            fontWeight    = FontWeight.Medium,
            letterSpacing = 4.2.sp,
            textAlign     = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab row (LOGIN / SIGN UP)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TabRow(
    activeTab  : AuthTab,
    onTabChange: (AuthTab) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AuthTab.values().forEach { tab ->
            val isActive = tab == activeTab
            val underlineColor by animateColorAsState(
                targetValue   = if (isActive) Red else Color.Transparent,
                animationSpec = tween(250),
                label         = "underline${tab.name}"
            )
            val textColor by animateColorAsState(
                targetValue   = if (isActive) Red else TextMuted,
                animationSpec = tween(250),
                label         = "tabText${tab.name}"
            )
            val tabBgColor by animateColorAsState(
                targetValue   = if (isActive) RedAlpha05 else Color.Transparent,
                animationSpec = tween(250),
                label         = "tabBg${tab.name}"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(tabBgColor)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onTabChange(tab) }
                    .drawBehind {
                        drawLine(
                            color       = RedAlpha20,
                            start       = Offset(0f, size.height),
                            end         = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color       = underlineColor,
                            start       = Offset(0f, size.height - 1.5.dp.toPx()),
                            end         = Offset(size.width, size.height - 1.5.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text          = if (tab == AuthTab.LOGIN) "LOGIN" else "SIGN UP",
                    color         = textColor,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Login tab
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoginTabContent(
    email            : String,
    onEmailChange    : (String) -> Unit,
    password         : String,
    onPasswordChange : (String) -> Unit,
    showPassword     : Boolean,
    onTogglePassword : () -> Unit,
    onLoginClick     : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WelcomeHeading(
            title    = "Secure Access",
            subtitle = "Protect your digital identity with our\nencrypted cloud gateway."
        )

        AuthInputField(
            label         = "Email Address",
            value         = email,
            placeholder   = "you@example.com",
            onValueChange = onEmailChange,
            keyboardType  = KeyboardType.Email
        )

        AuthInputField(
            label            = "Password",
            value            = password,
            placeholder      = "••••••••",
            onValueChange    = onPasswordChange,
            keyboardType     = KeyboardType.Password,
            isPassword       = true,
            showPassword     = showPassword,
            onTogglePassword = onTogglePassword
        )

        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {},
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text       = "Forgot Password?",
                color      = Red,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        PrimaryButton(text = "Log In", onClick = onLoginClick)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sign-up tab
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignupTabContent(
    email            : String,
    onEmailChange    : (String) -> Unit,
    password         : String,
    onPasswordChange : (String) -> Unit,
    confirmPass      : String,
    onConfirmChange  : (String) -> Unit,
    showPassword     : Boolean,
    onTogglePassword : () -> Unit,
    onSignupClick    : () -> Unit
) {
    val strength       = remember(password) { getPasswordStrength(password) }
    val passwordsMatch = confirmPass.isEmpty() || confirmPass == password

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WelcomeHeading(
            title    = "Create Account",
            subtitle = "Join Nirbhay and stay protected\nwith your personal guardian network."
        )

        AuthInputField(
            label         = "Email Address",
            value         = email,
            placeholder   = "you@example.com",
            onValueChange = onEmailChange,
            keyboardType  = KeyboardType.Email
        )

        AuthInputField(
            label            = "Password",
            value            = password,
            placeholder      = "Create a strong password",
            onValueChange    = onPasswordChange,
            keyboardType     = KeyboardType.Password,
            isPassword       = true,
            showPassword     = showPassword,
            onTogglePassword = onTogglePassword
        )

        if (password.isNotEmpty()) {
            PasswordStrengthBar(strength = strength)
        }

        AuthInputField(
            label            = "Confirm Password",
            value            = confirmPass,
            placeholder      = "Re-enter your password",
            onValueChange    = onConfirmChange,
            keyboardType     = KeyboardType.Password,
            isPassword       = true,
            showPassword     = showPassword,
            onTogglePassword = onTogglePassword,
            isError          = !passwordsMatch
        )

        PrimaryButton(
            text    = "Create Account",
            onClick = onSignupClick,
            enabled = email.isNotEmpty() && password.isNotEmpty() && password == confirmPass
        )


        Text(
            text       = "By continuing you agree to our Terms of Service\nand Privacy Policy.",
            color      = TextSubtle,
            fontSize   = 11.sp,
            textAlign  = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Welcome heading
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WelcomeHeading(title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text       = title,
            color      = TextWhite,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Text(
            text       = subtitle,
            color      = TextMuted,
            fontSize   = 14.sp,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Input field
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AuthInputField(
    label           : String,
    value           : String,
    placeholder     : String,
    onValueChange   : (String) -> Unit,
    keyboardType    : KeyboardType   = KeyboardType.Text,
    isPassword      : Boolean        = false,
    showPassword    : Boolean        = false,
    onTogglePassword: () -> Unit     = {},
    isError         : Boolean        = false
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isError   -> ErrorRed
            isFocused -> FocusBorder
            else      -> RedAlpha30
        },
        animationSpec = tween(200),
        label         = "border$label"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isFocused) FocusBg else InputBg,
        animationSpec = tween(200),
        label         = "bg$label"
    )

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text       = label,
            color      = TextLabel,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        BasicTextField(
            value                = value,
            onValueChange        = onValueChange,
            singleLine           = true,
            visualTransformation = if (isPassword && !showPassword)
                PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(
                color      = TextWhite,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Normal
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                .onFocusChanged { state -> isFocused = state.isFocused }
                .padding(horizontal = 20.dp),
            decorationBox = { innerTextField ->
                Row(
                    modifier              = Modifier.fillMaxSize(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text     = placeholder,
                                color    = TextPlaceholder,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }

                    if (isPassword) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(
                                    indication        = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onTogglePassword() },
                            contentAlignment = Alignment.Center
                        ) {
                            EyeIcon(
                                tint     = if (showPassword) Red else TextSubtle,
                                crossed  = !showPassword,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        )

        AnimatedVisibility(visible = isError) {
            Text(
                text     = "Passwords do not match",
                color    = ErrorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Password strength helpers
// ─────────────────────────────────────────────────────────────────────────────
private enum class PasswordStrength { WEAK, FAIR, STRONG }

private fun getPasswordStrength(pass: String): PasswordStrength {
    var score = 0
    if (pass.length >= 8)                    score++
    if (pass.any { it.isUpperCase() })       score++
    if (pass.any { it.isDigit() })           score++
    if (pass.any { !it.isLetterOrDigit() })  score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score == 2 -> PasswordStrength.FAIR
        else       -> PasswordStrength.STRONG
    }
}

@Composable
private fun PasswordStrengthBar(strength: PasswordStrength) {
    val (label, barColor, fraction) = when (strength) {
        PasswordStrength.WEAK   -> Triple("Weak",   Color(0xFFEF4444), 0.33f)
        PasswordStrength.FAIR   -> Triple("Fair",   Color(0xFFF59E0B), 0.66f)
        PasswordStrength.STRONG -> Triple("Strong", Color(0xFF22C55E), 1.00f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = tween(400),
        label         = "strengthAnim"
    )

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Password strength", color = TextSubtle,  fontSize = 12.sp)
            Text(text = label,               color = barColor,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(RedAlpha20)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Primary button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PrimaryButton(
    text   : String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Red else Red.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = text,
            color      = Color.White,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Eye icon (password toggle)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EyeIcon(
    tint    : Color,
    crossed : Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w  = size.width
        val h  = size.height
        val cx = w / 2f
        val cy = h / 2f

        val eyePath = Path().apply {
            moveTo(0f, cy)
            cubicTo(cx * 0.5f, cy - h * 0.38f, cx * 1.5f, cy - h * 0.38f, w, cy)
            cubicTo(cx * 1.5f, cy + h * 0.38f, cx * 0.5f, cy + h * 0.38f, 0f, cy)
            close()
        }
        drawPath(eyePath, tint, style = Stroke(1.5.dp.toPx()))
        drawCircle(tint, radius = w * 0.14f, center = Offset(cx, cy))

        if (crossed) {
            drawLine(
                color       = tint,
                start       = Offset(w * 0.15f, h * 0.85f),
                end         = Offset(w * 0.85f, h * 0.15f),
                strokeWidth = 1.5.dp.toPx(),
                cap         = StrokeCap.Round
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────
@Preview(
    showBackground = true,
    widthDp        = 390,
    heightDp       = 884,
    name           = "Login / Sign Up"
)
@Composable
fun LoginSignupPagePreview() {
    LoginSignupPage()
}