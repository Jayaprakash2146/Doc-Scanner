package com.docscanner.app.scanner.detection

import androidx.camera.core.ImageProxy
import com.docscanner.app.camera.ImageAnalysisUtils
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.scanner.opencv.BitmapMatUtils
import com.docscanner.app.scanner.opencv.DocumentEdgeDetector
import com.docscanner.app.scanner.opencv.OpenCvBootstrap

class RealtimeDocumentDetector(
    private val edgeDetector: DocumentEdgeDetector = DocumentEdgeDetector()
) {
    private val autoCaptureTracker = AutoCaptureTracker(holdDurationMs = 2000L)
    private var frameIndex = 0

    @Synchronized
    fun analyze(imageProxy: ImageProxy): DocumentDetectionResult? {
        if (!OpenCvBootstrap.isReady) return null

        frameIndex++
        if (frameIndex % 2 != 0 && lastResult != null) {
            return lastResult
        }

        val bitmap = ImageAnalysisUtils.toBitmap(imageProxy) ?: run {
            autoCaptureTracker.reset()
            lastResult = null
            return null
        }

        val analysis = BitmapMatUtils.downscale(bitmap, 1280)
        if (analysis !== bitmap) bitmap.recycle()

        val corners = edgeDetector.detectCorners(analysis, realtime = true)
        val frameW = analysis.width
        val frameH = analysis.height
        if (!analysis.isRecycled) analysis.recycle()

        if (corners == null || corners.size != 4) {
            autoCaptureTracker.reset()
            lastResult = null
            return null
        }

        val normalized = corners.map { p ->
            Point2D(p.x / frameW, p.y / frameH)
        }

        val coverage = polygonArea(normalized)
        if (coverage < 0.04f || coverage > 0.98f) {
            autoCaptureTracker.reset()
            lastResult = null
            return null
        }

        val progress = autoCaptureTracker.onDocumentVisible(true)
        val stable = progress >= 1f

        val result = DocumentDetectionResult(
            corners = normalized,
            isStable = stable,
            coverage = coverage,
            frameWidth = frameW,
            frameHeight = frameH,
            stabilityProgress = progress
        )
        lastResult = result
        return result
    }

    private var lastResult: DocumentDetectionResult? = null

    fun reset() {
        autoCaptureTracker.reset()
        lastResult = null
        frameIndex = 0
    }

    private fun polygonArea(points: List<Point2D>): Float {
        var sum = 0f
        for (i in points.indices) {
            val j = (i + 1) % points.size
            sum += points[i].x * points[j].y - points[j].x * points[i].y
        }
        return kotlin.math.abs(sum) / 2f
    }
}
