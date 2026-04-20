package com.pixelmentor.app.ui.analyze

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pixelmentor.app.domain.model.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalysisResultsScreen(
    onBack: () -> Unit,
    onAnalyzeAnother: () -> Unit,
    onLessonClick: (String) -> Unit,
    viewModel: PhotoAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = (uiState as? AnalysisUiState.Success)?.result ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Results", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        viewModel.reset()
                        onAnalyzeAnother()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze Another Photo", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Score card ──────────────────────────────────────────────────
            CompositionScoreCard(score = result.compositionScore)

            // ── Vision tags ─────────────────────────────────────────────────
            if (result.visionTags.isNotEmpty()) {
                VisionTagsRow(tags = result.visionTags)
            }

            // ── Feedback ────────────────────────────────────────────────────
            FeedbackSection(feedback = result.feedback)

            // ── Edit suggestions ────────────────────────────────────────────
            EditSuggestionsSection(suggestions = result.editSuggestions)

            // ── Lesson recommendations ───────────────────────────────────────
            if (result.lessonRecommendations.isNotEmpty()) {
                LessonRecommendationsSection(
                    lessons = result.lessonRecommendations,
                    onLessonClick = onLessonClick
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composition score ring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompositionScoreCard(score: Int) {
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "score"
    )
    val scoreColor = when {
        score >= 80 -> Color(0xFF22C55E)  // green
        score >= 60 -> Color(0xFFF59E0B)  // amber
        else -> Color(0xFFEF4444)         // red
    }
    val scoreLabel = when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else -> "Needs Work"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress
            ScoreRing(
                progress = animatedScore / 100f,
                score = animatedScore,
                color = scoreColor,
                size = 100.dp
            )
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    "Composition Score",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    scoreLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Out of 100 based on composition, lighting, color and focus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScoreRing(
    progress: Float,
    score: Int,
    color: Color,
    size: Dp
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vision tags
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisionTagsRow(tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(icon = Icons.Outlined.Label, title = "Detected")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.take(10).forEach { tag ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feedback
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeedbackSection(feedback: List<FeedbackItem>) {
    val strengths = feedback.filter { it.type == "strength" }
    val improvements = feedback.filter { it.type == "improvement" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(icon = Icons.Outlined.Feedback, title = "Feedback")

        if (strengths.isNotEmpty()) {
            FeedbackGroup(
                label = "Strengths",
                items = strengths,
                chipColor = Color(0xFF22C55E),
                bgColor = Color(0xFF22C55E).copy(alpha = 0.08f)
            )
        }
        if (improvements.isNotEmpty()) {
            FeedbackGroup(
                label = "To Improve",
                items = improvements,
                chipColor = MaterialTheme.colorScheme.primary,
                bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun FeedbackGroup(
    label: String,
    items: List<FeedbackItem>,
    chipColor: Color,
    bgColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = chipColor,
            fontWeight = FontWeight.SemiBold
        )
        items.forEach { item ->
            FeedbackRow(item, chipColor, bgColor)
        }
    }
}

@Composable
private fun FeedbackRow(item: FeedbackItem, chipColor: Color, bgColor: Color) {
    val category = FeedbackCategory.from(item.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(category.emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                category.label,
                style = MaterialTheme.typography.labelSmall,
                color = chipColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit suggestions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditSuggestionsSection(suggestions: EditSuggestions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(icon = Icons.Outlined.Tune, title = "Edit Suggestions")

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sliders
                EditSlider("Exposure", suggestions.exposure, -3f, 3f, 3f)
                EditSlider("Contrast", suggestions.contrast.toFloat(), -100f, 100f, 100f)
                EditSlider("Highlights", suggestions.highlights.toFloat(), -100f, 100f, 100f)
                EditSlider("Shadows", suggestions.shadows.toFloat(), -100f, 100f, 100f)
                EditSlider("Whites", suggestions.whites.toFloat(), -100f, 100f, 100f)
                EditSlider("Blacks", suggestions.blacks.toFloat(), -100f, 100f, 100f)
                EditSlider("Clarity", suggestions.clarity.toFloat(), -100f, 100f, 100f)
                EditSlider("Vibrance", suggestions.vibrance.toFloat(), -100f, 100f, 100f)
                EditSlider("Saturation", suggestions.saturation.toFloat(), -100f, 100f, 100f)

                HorizontalDivider()

                // Qualitative suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QualitativePill(
                        modifier = Modifier.weight(1f),
                        label = "Color Grade",
                        value = suggestions.colorGrade.replaceFirstChar { it.uppercaseChar() }
                    )
                    QualitativePill(
                        modifier = Modifier.weight(1f),
                        label = "Crop",
                        value = when (suggestions.cropSuggestion) {
                            "straighten" -> "Straighten"
                            "rule_of_thirds_reframe" -> "Rule of Thirds"
                            else -> "None"
                        }
                    )
                }

                // Estimated improvement blurb
                if (suggestions.estimatedImprovement.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            suggestions.estimatedImprovement,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    absMax: Float
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "slider_$label"
    )
    val normalized = (animatedValue - min) / (max - min)  // 0..1
    val centerNorm = (0f - min) / (max - min)              // position of zero

    val isPositive = animatedValue > 0
    val trackColor = if (isPositive) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.secondary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            val display = if (label == "Exposure")
                String.format("%+.1f", animatedValue)
            else
                String.format("%+.0f", animatedValue)
            Text(
                display,
                style = MaterialTheme.typography.bodySmall,
                color = trackColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Custom track with center anchor
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Filled segment from center to value
            val startFrac = if (isPositive) centerNorm else normalized
            val widthFrac = if (isPositive) normalized - centerNorm else centerNorm - normalized

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(startFrac + widthFrac)
                    .padding(start = (startFrac * 1000).dp / 1000)  // approximate
            )

            // Simpler: just use a LinearProgressIndicator with offset approach
            // Use Canvas instead for precision
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val totalW = size.width
                val centerX = totalW * centerNorm
                val valueX = totalW * normalized

                drawRoundRect(
                    color = trackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = minOf(centerX, valueX),
                        y = 0f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = kotlin.math.abs(valueX - centerX),
                        height = size.height
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                // Center tick
                drawLine(
                    color = trackColor.copy(alpha = 0.5f),
                    start = androidx.compose.ui.geometry.Offset(centerX, 0f),
                    end = androidx.compose.ui.geometry.Offset(centerX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun QualitativePill(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lesson recommendations
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LessonRecommendationsSection(
    lessons: List<LessonRecommendation>,
    onLessonClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(icon = Icons.Outlined.School, title = "Recommended Lessons")
        lessons.forEach { lesson ->
            LessonRecommendationCard(lesson = lesson, onClick = { onLessonClick(lesson.id) })
        }
    }
}

@Composable
private fun LessonRecommendationCard(lesson: LessonRecommendation, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!lesson.relevanceReason.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        lesson.relevanceReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!lesson.difficulty.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        lesson.difficulty.replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Open lesson",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
