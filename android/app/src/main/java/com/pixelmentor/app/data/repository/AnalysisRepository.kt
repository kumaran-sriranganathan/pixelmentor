package com.pixelmentor.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.domain.model.PhotoAnalysisRequest
import com.pixelmentor.app.domain.model.PhotoAnalysisResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisRepository @Inject constructor(
    private val apiService: PixelMentorApiService,
    @ApplicationContext private val context: Context
) {

    /**
     * Compress the URI to a JPEG (max 1024px on the long edge, 85% quality),
     * base64-encode it, then POST to the backend.
     */
    suspend fun analyzePhoto(uri: Uri): Result<PhotoAnalysisResponse> = withContext(Dispatchers.IO) {
        try {
            val base64 = uriToBase64(uri)
            val request = PhotoAnalysisRequest(imageBase64 = base64)
            val response = apiService.analyzePhoto(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun uriToBase64(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open URI: $uri")

        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val scaled = scaleBitmap(original, maxDimension = 1024)

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)

        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
