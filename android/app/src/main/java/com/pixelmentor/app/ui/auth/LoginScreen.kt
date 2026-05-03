package com.pixelmentor.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixelmentor.app.domain.model.AuthState

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) onAuthenticated()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Animated gradient background ──────────────────────────────────
        AnimatedBackground()

        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            // ── Logo / branding ───────────────────────────────────────────
            LogoBranding()

            Spacer(Modifier.height(48.dp))

            // ── Auth card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tab toggle
                    AuthTabToggle(
                        isSignUp = isSignUp,
                        onToggle = { isSignUp = it }
                    )

                    // Error message
                    AnimatedVisibility(visible = uiState is LoginUiState.Error) {
                        if (uiState is LoginUiState.Error) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = (uiState as LoginUiState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    when (authState) {
                        is AuthState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is AuthState.Unauthenticated -> {
                            // Email field
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            // Password field
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Outlined.VisibilityOff
                                            else Icons.Outlined.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password"
                                                                 else "Show password",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible)
                                    VisualTransformation.None
                                else
                                    PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(Modifier.height(4.dp))

                            // Primary CTA button
                            Button(
                                onClick = {
                                    if (isSignUp) viewModel.signUp(email, password)
                                    else viewModel.signInWithEmail(email, password)
                                },
                                enabled = uiState !is LoginUiState.SigningIn &&
                                        email.isNotBlank() && password.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (uiState is LoginUiState.SigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isSignUp) "Create Account" else "Sign In",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            // Divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    "or",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }

                            // Google button
                            OutlinedButton(
                                onClick = { viewModel.signInWithGoogle() },
                                enabled = uiState !is LoginUiState.SigningIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Google G logo using text
                                    Text(
                                        "G",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFF4285F4)
                                    )
                                    Text(
                                        "Continue with Google",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        is AuthState.Authenticated -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Toggle sign in / sign up
            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(
                    text = if (isSignUp) "Already have an account? Sign In"
                           else "Don't have an account? Sign Up",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Feature highlights at the bottom
            FeatureHighlights()

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animated gradient background
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val background = MaterialTheme.colorScheme.background

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Base gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.15f),
                    background,
                    tertiary.copy(alpha = 0.08f)
                )
            )
        )
        // Animated floating circles
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                radius = size.width * 0.6f
            ),
            radius = size.width * 0.6f,
            center = Offset(
                x = size.width * (0.2f + offset * 0.3f),
                y = size.height * 0.2f
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                radius = size.width * 0.5f
            ),
            radius = size.width * 0.5f,
            center = Offset(
                x = size.width * (0.8f - offset * 0.2f),
                y = size.height * 0.7f
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Logo and branding
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogoBranding() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Camera aperture icon in a gradient circle
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = Color.White
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "PixelMentor",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "AI-powered photography education",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign In / Sign Up tab toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuthTabToggle(isSignUp: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        listOf(false to "Sign In", true to "Sign Up").forEach { (value, label) ->
            val selected = isSignUp == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface
                        else Color.Transparent
                    )
                    .clickable { onToggle(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature highlights
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeatureHighlights() {
    val features = listOf(
        Icons.Outlined.AutoAwesome to "AI Photo Analysis",
        Icons.Outlined.School to "Expert Lessons",
        Icons.Outlined.SmartToy to "Personal Tutor"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        features.forEach { (icon, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
