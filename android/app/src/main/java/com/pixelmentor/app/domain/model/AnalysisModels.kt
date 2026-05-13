package com.pixelmentor.app.domain.model

import com.google.gson.annotations.SerializedName

// ── Request ──────────────────────────────────────────────────────────────────

data class PhotoAnalysisRequest(
    @SerializedName("image_base64") val imageBase64: String,
    @SerializedName("filename") val filename: String = "photo.jpg",
    @SerializedName("mime_type") val mimeType: String = "image/jpeg"
)

// ── Response ──────────────────────────────────────────────────────────────────

data class PhotoAnalysisResponse(
    @SerializedName("analysis_id") val analysisId: String,
    @SerializedName("composition_score") val compositionScore: Int,
    @SerializedName("feedback") val feedback: List<FeedbackItem>,
    @SerializedName("edit_suggestions") val editSuggestions: EditSuggestions,
    @SerializedName("lesson_recommendations") val lessonRecommendations: List<LessonRecommendation>,
    @SerializedName("vision_tags") val visionTags: List<String>
)

data class FeedbackItem(
    @SerializedName("text") val text: String,
    @SerializedName("type") val type: String,        // "strength" | "improvement"
    @SerializedName("category") val category: String  // "composition" | "lighting" | "color" | "focus"
)

data class EditSuggestions(
    @SerializedName("exposure") val exposure: Float,
    @SerializedName("contrast") val contrast: Int,
    @SerializedName("highlights") val highlights: Int,
    @SerializedName("shadows") val shadows: Int,
    @SerializedName("whites") val whites: Int,
    @SerializedName("blacks") val blacks: Int,
    @SerializedName("clarity") val clarity: Int,
    @SerializedName("vibrance") val vibrance: Int,
    @SerializedName("saturation") val saturation: Int,
    @SerializedName("color_grade") val colorGrade: String,       // "warm" | "cool" | "neutral"
    @SerializedName("crop_suggestion") val cropSuggestion: String, // "straighten" | "rule_of_thirds_reframe" | "none"
    @SerializedName("estimated_improvement") val estimatedImprovement: String
)

data class LessonRecommendation(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("relevance_reason") val relevanceReason: String? = null,
    @SerializedName("difficulty") val difficulty: String? = null
)

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class AnalysisUiState {
    object Idle : AnalysisUiState()
    object Uploading : AnalysisUiState()       // compressing + encoding
    object Analyzing : AnalysisUiState()       // waiting for API
    data class Success(val result: PhotoAnalysisResponse) : AnalysisUiState()
    data class Error(val message: String) : AnalysisUiState()
}

enum class FeedbackCategory(val label: String, val emoji: String) {
    COMPOSITION("Composition", "📐"),
    LIGHTING("Lighting", "💡"),
    COLOR("Color", "🎨"),
    FOCUS("Focus", "🔍");

    companion object {
        fun from(raw: String) = entries.firstOrNull {
            it.name.equals(raw, ignoreCase = true)
        } ?: COMPOSITION
    }
}
