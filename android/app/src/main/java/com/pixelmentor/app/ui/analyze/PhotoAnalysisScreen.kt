package com.pixelmentor.app.ui.analyze

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.*
import com.pixelmentor.app.domain.model.AnalysisUiState
import com.pixelmentor.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PhotoAnalysisScreen(
    onNavigateToResults: () -> Unit,
    viewModel: PhotoAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedUri by viewModel.selectedImageUri.collectAsState()

    // Navigate when analysis succeeds
    LaunchedEffect(uiState) {
        if (uiState is AnalysisUiState.Success) onNavigateToResults()
    }

    // Camera URI holder
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { viewModel.onImageSelected(it) } }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Storage permission (pre-API 33)
    val storagePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE) { granted ->
            if (granted) galleryLauncher.launch("image/*")
        }
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyze Photo", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is AnalysisUiState.Uploading,
                is AnalysisUiState.Analyzing -> AnalyzingOverlay(state)

                is AnalysisUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PickerContent(
                            selectedUri = selectedUri,
                            onGalleryClick = {
                                if (storagePermission != null &&
                                    !storagePermission.status.isGranted
                                ) storagePermission.launchPermissionRequest()
                                else galleryLauncher.launch("image/*")
                            },
                            onCameraClick = {
                                if (!cameraPermission.status.isGranted)
                                    cameraPermission.launchPermissionRequest()
                                else {
                                    val file = File(
                                        context.cacheDir,
                                        "camera_photo_${System.currentTimeMillis()}.jpg"
                                    )
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.provider", file
                                    )
                                    cameraUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            },
                            onAnalyzeClick = { viewModel.analyzePhoto() }
                        )
                        Spacer(Modifier.height(16.dp))
                        ErrorBanner(state.message) { viewModel.retryAnalysis() }
                    }
                }

                else -> PickerContent(
                    selectedUri = selectedUri,
                    onGalleryClick = {
                        if (storagePermission != null && !storagePermission.status.isGranted)
                            storagePermission.launchPermissionRequest()
                        else galleryLauncher.launch("image/*")
                    },
                    onCameraClick = {
                        if (!cameraPermission.status.isGranted)
                            cameraPermission.launchPermissionRequest()
                        else {
                            val file = File(
                                context.cacheDir,
                                "camera_photo_${System.currentTimeMillis()}.jpg"
                            )
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file
                            )
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    },
                    onAnalyzeClick = { viewModel.analyzePhoto() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main picker content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PickerContent(
    selectedUri: Uri?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAnalyzeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Photo preview / drop zone
        PhotoDropZone(
            uri = selectedUri,
            onGalleryClick = onGalleryClick,
            onCameraClick = onCameraClick
        )

        // Source buttons (always visible)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SourceButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PhotoLibrary,
                label = "Gallery",
                onClick = onGalleryClick
            )
            SourceButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.CameraAlt,
                label = "Camera",
                onClick = onCameraClick
            )
        }

        // Analyze CTA — only shown when image is selected
        AnimatedVisibility(
            visible = selectedUri != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Button(
                onClick = onAnalyzeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Analyze Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Hint text
        if (selectedUri == null) {
            Text(
                "Select or capture a photo to get AI-powered\ncomposition feedback and edit suggestions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drop zone / preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoDropZone(
    uri: Uri?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    val infiniteAnim = rememberInfiniteTransition(label = "border")
    val borderAlpha by infiniteAnim.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (uri == null) Modifier.border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = borderAlpha)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) else Modifier
            )
            .background(
                if (uri == null) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(enabled = uri == null, onClick = onGalleryClick),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "Selected photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Overlay "Change" button in top-right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                SmallIconButton(Icons.Outlined.PhotoLibrary, "Gallery", onGalleryClick)
                Spacer(Modifier.width(8.dp))
                SmallIconButton(Icons.Outlined.CameraAlt, "Camera", onCameraClick)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Tap to select a photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SourceButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Analyzing overlay (loading)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnalyzingOverlay(state: AnalysisUiState) {
    val label = when (state) {
        is AnalysisUiState.Uploading -> "Preparing image…"
        else -> "Analyzing with AI…"
    }
    val sublabel = when (state) {
        is AnalysisUiState.Uploading -> "Compressing and encoding"
        else -> "This takes 10–20 seconds"
    }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            sublabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Analysis failed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
