package com.docscanner.app.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.docscanner.app.domain.model.Point2D

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dimPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val handles = Array(4) { PointF(0f, 0f) }
    private var imageWidth = 1f
    private var imageHeight = 1f
    private var activeHandle = -1
    private var pendingPixelCorners: List<Point2D>? = null
    private var cornersReady = false
    var onCornersChanged: ((List<Point2D>) -> Unit)? = null

    fun setImageSize(w: Int, h: Int) {
        imageWidth = w.toFloat().coerceAtLeast(1f)
        imageHeight = h.toFloat().coerceAtLeast(1f)
        applyPendingCornersIfReady()
    }

    /** Corners in 0..1 relative to image width/height. */
    fun setCornersNormalized(corners: List<Point2D>) {
        if (corners.size != 4) return
        val rect = imageDisplayRect()
        for (i in 0 until 4) {
            handles[i].x = rect.left + corners[i].x * rect.width()
            handles[i].y = rect.top + corners[i].y * rect.height()
        }
        invalidate()
    }

    /** Corners in image pixel coordinates. */
    fun setCornersPixels(corners: List<Point2D>) {
        if (corners.size != 4) return
        pendingPixelCorners = corners
        applyPendingCornersIfReady()
    }

    private fun applyPendingCornersIfReady() {
        val corners = pendingPixelCorners ?: return
        if (width <= 0 || height <= 0) return
        val rect = imageDisplayRect()
        if (rect.width() <= 0f || rect.height() <= 0f) return
        for (i in 0 until 4) {
            handles[i].x = rect.left + (corners[i].x / imageWidth) * rect.width()
            handles[i].y = rect.top + (corners[i].y / imageHeight) * rect.height()
        }
        cornersReady = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyPendingCornersIfReady()
    }

    fun getCornersNormalized(): List<Point2D> {
        val rect = imageDisplayRect()
        return handles.map { p ->
            Point2D(
                ((p.x - rect.left) / rect.width()).coerceIn(0f, 1f),
                ((p.y - rect.top) / rect.height()).coerceIn(0f, 1f)
            )
        }
    }

    fun getCornersPixels(): List<Point2D> {
        return getCornersNormalized().map { n ->
            Point2D(n.x * imageWidth, n.y * imageHeight)
        }
    }

    private fun imageDisplayRect(): android.graphics.RectF {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val imageRatio = imageWidth / imageHeight
        val viewRatio = viewW / viewH
        return if (imageRatio > viewRatio) {
            val h = viewW / imageRatio
            android.graphics.RectF(0f, (viewH - h) / 2f, viewW, (viewH + h) / 2f)
        } else {
            val w = viewH * imageRatio
            android.graphics.RectF((viewW - w) / 2f, 0f, (viewW + w) / 2f, viewH)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> activeHandle = nearestHandle(event.x, event.y)
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle >= 0) {
                    handles[activeHandle].x = event.x
                    handles[activeHandle].y = event.y
                    invalidate()
                    onCornersChanged?.invoke(getCornersPixels())
                }
            }
            MotionEvent.ACTION_UP -> activeHandle = -1
        }
        return true
    }

    private fun nearestHandle(x: Float, y: Float): Int {
        var best = -1
        var bestDist = 80f * 80f
        handles.forEachIndexed { i, p ->
            val dx = p.x - x
            val dy = p.y - y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cornersReady || width <= 0 || height <= 0) return

        val docPath = Path().apply {
            moveTo(handles[0].x, handles[0].y)
            for (i in 1 until 4) lineTo(handles[i].x, handles[i].y)
            close()
        }

        val dimPath = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addPath(docPath)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(dimPath, dimPaint)
        canvas.drawPath(docPath, linePaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        handles.forEach {
            canvas.drawCircle(it.x, it.y, 22f, cornerPaint)
            canvas.drawCircle(it.x, it.y, 22f, strokePaint)
        }
    }
}
