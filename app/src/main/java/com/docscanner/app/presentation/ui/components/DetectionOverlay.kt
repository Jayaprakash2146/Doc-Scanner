package com.docscanner.app.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.docscanner.app.domain.model.Point2D

@Composable
fun DetectionOverlay(
    corners: List<Point2D>?,
    imageWidth: Int,
    imageHeight: Int
) {
    if (corners == null || corners.size != 4) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        // Analyzer bitmap usually has different orientation or size
        // We expect corners relative to imageWidth/imageHeight
        val scaleX = screenWidth / imageWidth
        val scaleY = screenHeight / imageHeight
        
        // This is a simple scaling, might need adjustment for fit/fill
        val points = corners.map { 
            Offset(it.x * scaleX, it.y * scaleY)
        }

        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }

        drawPath(
            path = path,
            color = Color.Cyan.copy(alpha = 0.5f),
            style = Stroke(width = 4.dp.toPx())
        )
        
        points.forEach { pt ->
            drawCircle(Color.Cyan, radius = 6.dp.toPx(), center = pt)
        }
    }
}
