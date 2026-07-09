package com.docscanner.app.camera

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.docscanner.app.scanner.detection.DocumentDetectionResult
import com.docscanner.app.scanner.detection.RealtimeDocumentDetector
import com.docscanner.app.scanner.opencv.OpenCvBootstrap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class FlashMode { OFF, ON, AUTO }

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onDetection: (DocumentDetectionResult?) -> Unit,
    private val onAutoCaptureReady: () -> Unit
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector = RealtimeDocumentDetector()
    private val isCapturing = AtomicBoolean(false)

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashMode = FlashMode.OFF
    private var lastAutoCaptureMs = 0L
    private val autoCaptureCooldownMs = 3500L
    var autoCaptureEnabled = false
    var enableAnalysis = false

    fun start() {
        OpenCvBootstrap.init()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(92)
                .build()
            imageCapture = capture

            provider.unbindAll()
            if (enableAnalysis) {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { proxy ->
                            analyzeFrame(proxy)
                        }
                    }
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )
            } else {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
            }
            applyFlash()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        detector.reset()
    }

    fun cycleFlash(): FlashMode {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        applyFlash()
        return flashMode
    }

    fun capture(onBitmap: (android.graphics.Bitmap?) -> Unit) {
        if (!isCapturing.compareAndSet(false, true)) {
            onBitmap(null)
            return
        }
        val capture = imageCapture ?: run {
            isCapturing.set(false)
            onBitmap(null)
            return
        }
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = CameraImageUtils.imageProxyToBitmap(image)
                    image.close()
                    isCapturing.set(false)
                    ContextCompat.getMainExecutor(context).execute {
                        onBitmap(bitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing.set(false)
                    ContextCompat.getMainExecutor(context).execute { onBitmap(null) }
                }
            }
        )
    }

    fun resetAutoCaptureCooldown() {
        lastAutoCaptureMs = 0L
        detector.reset()
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            if (!OpenCvBootstrap.isReady) {
                ContextCompat.getMainExecutor(context).execute { onDetection(null) }
                return
            }
            val result = detector.analyze(proxy)
            ContextCompat.getMainExecutor(context).execute {
                onDetection(result)
                val now = SystemClock.elapsedRealtime()
                if (autoCaptureEnabled &&
                    result != null &&
                    result.isStable &&
                    now - lastAutoCaptureMs > autoCaptureCooldownMs &&
                    !isCapturing.get()
                ) {
                    lastAutoCaptureMs = now
                    detector.reset()
                    onAutoCaptureReady()
                }
            }
        } catch (_: Exception) {
            ContextCompat.getMainExecutor(context).execute { onDetection(null) }
        } finally {
            proxy.close()
        }
    }

    private fun applyFlash() {
        val cam = camera ?: return
        when (flashMode) {
            FlashMode.OFF -> cam.cameraControl.enableTorch(false)
            FlashMode.ON -> cam.cameraControl.enableTorch(true)
            FlashMode.AUTO -> cam.cameraControl.enableTorch(false)
        }
    }
}
