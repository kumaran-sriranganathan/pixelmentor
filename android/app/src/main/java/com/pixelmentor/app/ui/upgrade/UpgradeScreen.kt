package com.pixelmentor.app.ui.upgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(onBack: () -> Unit) {
    var selectedPlan by remember { mutableStateOf(UpgradePlan.PRO) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade Plan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Hero ──────────────────────────────────────────────────────
            UpgradeHero()

            // ── Plan selector ─────────────────────────────────────────────
            PlanSelector(
                selectedPlan = selectedPlan,
                onPlanSelected = { selectedPlan = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ── Feature comparison ────────────────────────────────────────
            FeatureList(
                selectedPlan = selectedPlan,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ── CTA button ────────────────────────────────────────────────
            UpgradeCta(
                selectedPlan = selectedPlan,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ── Fine print ────────────────────────────────────────────────
            Text(
                text = "Cancel anytime. Billed monthly. Subscriptions managed via Google Play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UpgradeHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    )
                )
            )
            .padding(vertical = 32.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "📸", fontSize = 48.sp)
            Text(
                text = "Unlock Your Full Potential",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Get access to pro lessons, unlimited photo analysis, and expert AI coaching.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plan selector tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlanSelector(
    selectedPlan: UpgradePlan,
    onPlanSelected: (UpgradePlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UpgradePlan.entries.forEach { plan ->
            PlanTab(
                plan = plan,
                isSelected = plan == selectedPlan,
                onClick = { onPlanSelected(plan) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlanTab(
    plan: UpgradePlan,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Card(
        onClick = onClick,
        modifier = modifier.border(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
            shape = RoundedCornerShape(16.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = plan.emoji, fontSize = 28.sp)
            Text(
                text = plan.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
            )
            Text(
                text = plan.price,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            if (plan == UpgradePlan.PREMIUM) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                ) {
                    Text(
                        text = "BEST VALUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeatureList(
    selectedPlan: UpgradePlan,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "What's included",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            selectedPlan.features.forEach { feature ->
                FeatureRow(feature = feature)
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: PlanFeature) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (feature.included) Color(0xFF22C55E).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (feature.included) Icons.Outlined.Check else Icons.Outlined.Close,
                contentDescription = null,
                tint = if (feature.included) Color(0xFF22C55E)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (feature.included) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (feature.subtitle != null) {
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (feature.included) 1f else 0.4f
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CTA button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UpgradeCta(
    selectedPlan: UpgradePlan,
    modifier: Modifier = Modifier,
) {
    var showComingSoon by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { showComingSoon = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = "Start ${selectedPlan.label} — ${selectedPlan.price}/mo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (showComingSoon) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Billing is coming soon! We'll notify you when subscriptions are available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

enum class UpgradePlan(
    val label: String,
    val emoji: String,
    val price: String,
    val features: List<PlanFeature>,
) {
    PRO(
        label = "Pro",
        emoji = "⚡",
        price = "$9.99",
        features = listOf(
            PlanFeature("All Pro lessons", "30+ advanced lessons unlocked", included = true),
            PlanFeature("Unlimited photo analysis", "Full AI vision feedback", included = true),
            PlanFeature("AI Tutor", "Chat with your photography coach", included = true),
            PlanFeature("Lesson completion tracking", "Track your progress", included = true),
            PlanFeature("Quiz & assessments", "Test your knowledge", included = true),
            PlanFeature("Unlimited AI Tutor sessions", "Premium only", included = false),
            PlanFeature("Before/after comparison", "Premium only", included = false),
        )
    ),
    PREMIUM(
        label = "Premium",
        emoji = "👑",
        price = "$19.99",
        features = listOf(
            PlanFeature("Everything in Pro", "All Pro features included", included = true),
            PlanFeature("Unlimited AI Tutor sessions", "No daily limits", included = true),
            PlanFeature("Before/after comparison", "Compare your photo improvements", included = true),
            PlanFeature("Priority analysis", "Faster AI powered responses", included = true),
            PlanFeature("Early access to new features", "Be first to try new tools", included = true),
            PlanFeature("Export analysis reports", "Save and share your feedback", included = true),
        )
    ),
}

data class PlanFeature(
    val title: String,
    val subtitle: String? = null,
    val included: Boolean,
)
