package com.docscanner.app.scanner.detection

import com.docscanner.app.domain.model.Point2D
import kotlin.math.abs

class StabilityTracker(
    private val stableDurationMs: Long = 2000L,
    private val maxCornerDelta: Float = 0.06f
) {
    private var lastCorners: List<Point2D>? = null
    private var stableStartMs: Long = 0L

    fun update(corners: List<Point2D>?): Boolean {
        if (corners == null || corners.size != 4) {
            lastCorners = null
            stableStartMs = 0L
            return false
        }
        val now = System.currentTimeMillis()
        if (lastCorners == null || !isSimilar(lastCorners!!, corners)) {
            lastCorners = corners
            stableStartMs = now
            return false
        }
        return now - stableStartMs >= stableDurationMs
    }

    fun reset() {
        lastCorners = null
        stableStartMs = 0L
    }

    fun getProgress(): Float {
        if (stableStartMs == 0L) return 0f
        val elapsed = System.currentTimeMillis() - stableStartMs
        return (elapsed.toFloat() / stableDurationMs).coerceIn(0f, 1f)
    }

    private fun isSimilar(a: List<Point2D>, b: List<Point2D>): Boolean {
        for (i in 0 until 4) {
            if (abs(a[i].x - b[i].x) > maxCornerDelta) return false
            if (abs(a[i].y - b[i].y) > maxCornerDelta) return false
        }
        return true
    }
}
