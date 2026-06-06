package com.pixelmentor.app.ui.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.pixelmentor.app.BuildConfig
import com.pixelmentor.app.domain.model.*
import com.pixelmentor.app.ui.profile.DeleteAccountState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onUpgrade: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val deleteAccountState by viewModel.deleteAccountState.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Refresh stats every time this screen enters composition (e.g. tab switch)
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val state = uiState) {
            is ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ProfileUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = viewModel::loadProfile) {
                            Text("Retry")
                        }
                    }
                }
            }
            is ProfileUiState.Success -> {
                ProfileContent(
                    profile = state.profile,
                    userEmail = userEmail,
                    onUpgrade = onUpgrade,
                    onDeleteAccount = { showDeleteConfirmDialog = true },
                    modifier = Modifier.padding(padding)
                )
            }
        }

        // ── Sign out confirmation dialog ───────────────────────────────────
        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                icon = {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Sign Out?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "You'll need to sign in again to access PixelMentor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                    }) {
                        Text("Sign Out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Delete confirmation dialog ─────────────────────────────────────
        if (showDeleteConfirmDialog) {
            DeleteAccountDialog(
                isDeleting = deleteAccountState is DeleteAccountState.Deleting,
                onConfirm = {
                    showDeleteConfirmDialog = false
                    viewModel.deleteAccount(onAccountDeleted)
                },
                onDismiss = {
                    showDeleteConfirmDialog = false
                    viewModel.resetDeleteAccountState()
                }
            )
        }

        // ── Error snackbar ────────────────────────────────────────────────
        if (deleteAccountState is DeleteAccountState.Error) {
            LaunchedEffect(deleteAccountState) {
                // Reset after showing error so it doesn't persist
                kotlinx.coroutines.delay(3000)
                viewModel.resetDeleteAccountState()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileContent(
    profile: UserProfile,
    userEmail: String = "",
    onUpgrade: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileHeader(profile = profile)
        StatsRow(profile = profile)
        SkillLevelCard(skillLevel = profile.skillLevel)
        PlanCard(plan = profile.plan, photosUsed = profile.photosAnalyzedThisMonth, quizzesUsed = profile.quizzesUsedThisMonth, quizLimit = profile.quizLimit, onUpgrade = onUpgrade)
        ActivitySection(profile = profile)
        SupportSection(userEmail = userEmail, plan = profile.plan.value)
        DeleteAccountSection(onDeleteAccount = onDeleteAccount)
        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(profile: UserProfile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar circle with initials
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.displayName
                        .split(" ")
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                        .ifEmpty { "?" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (profile.plan != Plan.FREE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = profile.plan.emoji,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${profile.plan.label} Member",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(profile: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top row: monthly photos + lessons + streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = profile.photosAnalyzedThisMonth.toString(),
                label = "Photos\nThis Month",
                icon = Icons.Outlined.CameraAlt,
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = profile.lessonsCompleted.toString(),
                label = "Lessons\nCompleted",
                icon = Icons.Outlined.MenuBook,
                color = MaterialTheme.colorScheme.secondary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "${profile.streakDays}d",
                label = "Current\nStreak",
                icon = Icons.Outlined.Whatshot,
                color = Color(0xFFF59E0B)
            )
        }

        // All-time photos card — full width, visually distinct
        AllTimePhotosCard(totalPhotos = profile.photosAllTime)
    }
}

@Composable
private fun AllTimePhotosCard(totalPhotos: Int) {
    val color = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total Photos Analyzed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "All time across every session",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                text = totalPhotos.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skill level card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SkillLevelCard(skillLevel: SkillLevel) {
    val (label, progress, color) = when (skillLevel) {
        SkillLevel.BEGINNER -> Triple("Beginner", 0.25f, Color(0xFF22C55E))
        SkillLevel.INTERMEDIATE -> Triple("Intermediate", 0.6f, Color(0xFFF59E0B))
        SkillLevel.ADVANCED -> Triple("Advanced", 0.9f, MaterialTheme.colorScheme.primary)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Skill Level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(color.copy(alpha = 0.7f), color)
                            )
                        )
                )
            }

            Text(
                text = when (skillLevel) {
                    SkillLevel.BEGINNER -> "Keep shooting! Complete more lessons to level up."
                    SkillLevel.INTERMEDIATE -> "Great progress! You're developing a strong eye."
                    SkillLevel.ADVANCED -> "Impressive! You've mastered the fundamentals."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plan card
// ─────────────────────────────────────────────────────────────────────────────

private const val FREE_PLAN_MONTHLY_LIMIT = 7

@Composable
private fun PlanCard(
    plan: Plan,
    photosUsed: Int,
    quizzesUsed: Int,
    quizLimit: Int,
    onUpgrade: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (plan) {
                Plan.FREE -> MaterialTheme.colorScheme.surface
                Plan.PRO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                Plan.PREMIUM -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (plan) {
                                Plan.FREE -> "🆓"
                                Plan.PRO -> "⚡"
                                Plan.PREMIUM -> "👑"
                            },
                            fontSize = 22.sp
                        )
                    }
                    Column {
                        Text(
                            text = "${plan.label} Plan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (plan) {
                                Plan.FREE -> "Upgrade for unlimited features"
                                Plan.PRO -> "Advanced lessons + photo analysis"
                                Plan.PREMIUM -> "Everything + unlimited tutor"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (plan == Plan.FREE) {
                    FilledTonalButton(
                        onClick = onUpgrade,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Upgrade",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }

            // ── Monthly usage bars (free plan only) ──────────────────────────
            if (plan == Plan.FREE) {
                Spacer(Modifier.height(14.dp))
                PhotoUsageBar(used = photosUsed, limit = FREE_PLAN_MONTHLY_LIMIT)
                Spacer(Modifier.height(10.dp))
                QuizUsageBar(used = quizzesUsed, limit = quizLimit)
            }
        }
    }
}

@Composable
private fun PhotoUsageBar(used: Int, limit: Int) {
    val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
    val isNearLimit = used >= limit - 1
    val barColor = if (isNearLimit) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.primary

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "usageBar"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Photo analyses this month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$used / $limit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.7f), barColor)
                        )
                    )
            )
        }
        if (isNearLimit) {
            Text(
                text = if (used >= limit) "Limit reached — resets on the 1st"
                       else "1 analysis remaining this month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun QuizUsageBar(used: Int, limit: Int) {
    val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
    val isNearLimit = used >= limit - 1
    val barColor = if (isNearLimit) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.tertiary

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "quizUsageBar"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quiz attempts this month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$used / $limit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(barColor.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivitySection(profile: UserProfile) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Activity",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        ActivityRow(
            icon = Icons.Outlined.CameraAlt,
            title = "Photos Analyzed",
            subtitle = "${profile.photosAnalyzedThisMonth} this month · ${profile.photosAllTime} all time",
            value = profile.photosAllTime.toString(),
            color = MaterialTheme.colorScheme.primary
        )
        ActivityRow(
            icon = Icons.Outlined.School,
            title = "Lessons Completed",
            subtitle = "Photography fundamentals covered",
            value = profile.lessonsCompleted.toString(),
            color = MaterialTheme.colorScheme.secondary
        )
        ActivityRow(
            icon = Icons.Outlined.EmojiEvents,
            title = "Day Streak",
            subtitle = "Keep practicing every day",
            value = "${profile.streakDays} days",
            color = Color(0xFFF59E0B)
        )
    }
}

@Composable
private fun ActivityRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Support section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupportSection(
    userEmail: String,
    plan: String,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Support",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Contact support row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@pixelmentor.app")
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "PixelMentor Support Request"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    """
Hi PixelMentor Support,

Account: $userEmail
Plan: ${plan.replaceFirstChar { it.uppercase() }}
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE}
App version: ${BuildConfig.VERSION_NAME}

Describe your issue below:


                                    """.trimIndent()
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Contact Support")
                            )
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Contact Support",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "support@pixelmentor.app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // App version row (non-clickable, informational)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "App Version",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete account section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeleteAccountSection(onDeleteAccount: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onDeleteAccount,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Delete Account",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = "Permanently deletes your account and all associated data. This cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete account confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Delete Account?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "This will permanently delete your account, all your photos, " +
                "lesson progress, chat history, and analysis results.\n\n" +
                "This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Deleting...")
                } else {
                    Text("Yes, Delete Everything", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}
