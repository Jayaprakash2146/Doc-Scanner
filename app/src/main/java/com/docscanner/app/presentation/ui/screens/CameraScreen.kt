package com.docscanner.app.presentation.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.presentation.ui.components.AppAssets
import com.docscanner.app.presentation.ui.components.DetectionOverlay
import com.docscanner.app.presentation.ui.components.PngIconButton
import com.docscanner.app.presentation.ui.components.PngIconRow
import com.docscanner.app.scanner.opencv.DocumentEdgeDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onCaptured: (Bitmap) -> Unit,
    onGalleryImported: (Bitmap) -> Unit,
    showMultiPageOption: Boolean,
    onContinue: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            onGalleryImported(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    snackbar.showSnackbar("Failed to import image")
                }
            }
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { DocumentEdgeDetector() }

    var detectedCorners by remember { mutableStateOf<List<Point2D>?>(null) }
    var analysisSize by remember { mutableStateOf<Size?>(null) }
    var autoCaptureProgress by remember { mutableFloatStateOf(0f) }
    var lastDetectionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(detectedCorners) {
        if (detectedCorners != null && detectedCorners?.size == 4) {
            val start = System.currentTimeMillis()
            lastDetectionTime = start
            // Gradually increase progress over 2 seconds of detection
            while (System.currentTimeMillis() - start < 2000 && detectedCorners != null) {
                autoCaptureProgress = (System.currentTimeMillis() - start) / 2000f
                delay(50)
            }
            if (autoCaptureProgress >= 0.95f) {
                // Auto capture!
                val capture = imageCapture
                if (capture != null && !showMultiPageOption) {
                    capture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = imageProxyToBitmap(image)
                                image.close()
                                if (bitmap != null) {
                                    scope.launch { onCaptured(bitmap) }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {}
                        }
                    )
                }
            }
        } else {
            autoCaptureProgress = 0f
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Document") },
                actions = {
                    PngIconRow(modifier = Modifier.padding(end = 8.dp), spacing = 4.dp) {
                        PngIconButton(
                            iconRes = AppAssets.flash,
                            contentDescription = "Flash",
                            onClick = {
                                flashEnabled = !flashEnabled
                                imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                            },
                            size = 40.dp,
                            tint = if (flashEnabled) Color.Yellow else Color.White
                        )
                        PngIconButton(
                            iconRes = AppAssets.gallery,
                            contentDescription = "Gallery",
                            onClick = { galleryLauncher.launch("image/*") },
                            size = 40.dp
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasCameraPermission) {
                Text(
                    text = "Grant camera permission to continue",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                        .build()
                                    
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (!showMultiPageOption) {
                                            val bitmap = imageProxy.toBitmap()
                                            if (bitmap != null) {
                                                val corners = detector.detectCorners(bitmap, realtime = true)
                                                scope.launch {
                                                    detectedCorners = corners
                                                    analysisSize = Size(bitmap.width, bitmap.height)
                                                }
                                                bitmap.recycle()
                                            }
                                        } else {
                                            detectedCorners = null
                                        }
                                        imageProxy.close()
                                    }

                                    imageCapture = capture
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis,
                                            capture
                                        )
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "Lifecycle binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        }
                    )
                    
                    analysisSize?.let { size ->
                        DetectionOverlay(
                            corners = detectedCorners,
                            imageWidth = size.width,
                            imageHeight = size.height
                        )
                    }

                    // "Hold Steady" indicator
                    AnimatedVisibility(
                        visible = autoCaptureProgress > 0.1f,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "Hold Steady...",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = autoCaptureProgress,
                                    modifier = Modifier.width(120.dp),
                                    color = Color.Cyan,
                                    trackColor = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }

                if (!showMultiPageOption) {
                    PngIconButton(
                        iconRes = AppAssets.capture,
                        contentDescription = "Capture",
                        onClick = {
                            val capture = imageCapture ?: return@PngIconButton
                            capture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bitmap = imageProxyToBitmap(image)
                                        image.close()
                                        if (bitmap != null) {
                                            scope.launch { onCaptured(bitmap) }
                                        }
                                    }
                                    override fun onError(exception: ImageCaptureException) {}
                                }
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        size = 72.dp
                    )
                }
            }

            if (showMultiPageOption) {
                Dialog(onDismissRequest = { }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Page Captured",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onContinue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continue Scanning")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onStop,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop and Edit")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

    val matrix = Matrix()
    matrix.postRotate(imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotation = image.imageInfo.rotationDegrees
    if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return bitmap
}
