package com.docscanner.app.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.docscanner.app.R
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.scanner.detection.DocumentDetectionResult

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#00E5FF")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 200, 255)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00C8FF")
    }
    private val cornerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    
    private val path = Path()
    private val screenCorners = Array(4) { PointF() }
    private var corners: List<Point2D>? = null
    private var frameWidth = 1
    private var frameHeight = 1
    private var flashAlpha = 0f

    fun setDetection(result: DocumentDetectionResult?) {
        corners = result?.corners
        frameWidth = result?.frameWidth ?: 1
        frameHeight = result?.frameHeight ?: 1
        
        val progress = result?.stabilityProgress ?: 0f
        val alpha = (120 + progress * 135).toInt().coerceIn(120, 255)
        strokePaint.alpha = alpha
        fillPaint.alpha = (25 + progress * 45).toInt().coerceIn(25, 70)
        strokePaint.strokeWidth = 4f + progress * 3f

        invalidate()
    }

    fun playCaptureFlash() {
        ValueAnimator.ofFloat(0.7f, 0f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                flashAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (flashAlpha > 0f) {
            canvas.drawColor(Color.argb((flashAlpha * 255).toInt(), 255, 255, 255))
        }
        
        val pts = corners ?: return
        if (pts.size != 4) return

        mapToScreen(pts)
        path.reset()
        path.moveTo(screenCorners[0].x, screenCorners[0].y)
        for (i in 1 until 4) {
            path.lineTo(screenCorners[i].x, screenCorners[i].y)
        }
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        for (p in screenCorners) {
            canvas.drawCircle(p.x, p.y, 20f, cornerPaint)
            canvas.drawCircle(p.x, p.y, 20f, cornerStrokePaint)
        }
    }

    private fun mapToScreen(normalized: List<Point2D>) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val frameW = frameWidth.toFloat().coerceAtLeast(1f)
        val frameH = frameHeight.toFloat().coerceAtLeast(1f)
        
        // This mapping depends on how PreviewView is set up (usually fillCenter)
        val scale = maxOf(viewW / frameW, viewH / frameH)
        val scaledW = frameW * scale
        val scaledH = frameH * scale
        val offsetX = (viewW - scaledW) / 2f
        val offsetY = (viewH - scaledH) / 2f

        for (i in 0 until 4) {
            screenCorners[i].x = normalized[i].x * scaledW + offsetX
            screenCorners[i].y = normalized[i].y * scaledH + offsetY
        }
    }
}
