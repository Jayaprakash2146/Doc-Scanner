package com.docscanner.app.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.docscanner.app.domain.model.Point2D
import kotlin.math.sqrt

@Composable
fun CropOverlay(
    corners: List<Point2D>,
    onCornersUpdated: (List<Point2D>) -> Unit,
    imageWidth: Int,
    imageHeight: Int
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        val scaleX = screenWidth / imageWidth
        val scaleY = screenHeight / imageHeight
        val scale = minOf(scaleX, scaleY)

        val offsetX = (screenWidth - imageWidth * scale) / 2
        val offsetY = (screenHeight - imageHeight * scale) / 2

        var draggedCornerIndex by remember { mutableIntStateOf(-1) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(corners) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggedCornerIndex = corners.indexOfFirst { corner ->
                                val cx = corner.x * scale + offsetX
                                val cy = corner.y * scale + offsetY
                                val dist = sqrt((cx - offset.x).let { it * it } + (cy - offset.y).let { it * it })
                                dist < 40.dp.toPx()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (draggedCornerIndex != -1) {
                                change.consume()
                                val newCorners = corners.toMutableList()
                                val current = newCorners[draggedCornerIndex]
                                val updated = Point2D(
                                    x = (current.x + dragAmount.x / scale).coerceIn(0f, imageWidth.toFloat()),
                                    y = (current.y + dragAmount.y / scale).coerceIn(0f, imageHeight.toFloat())
                                )
                                newCorners[draggedCornerIndex] = updated
                                onCornersUpdated(newCorners)
                            }
                        },
                        onDragEnd = { draggedCornerIndex = -1 }
                    )
                }
        ) {
            val points = corners.map { Offset(it.x * scale + offsetX, it.y * scale + offsetY) }
            
            if (points.size == 4) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    lineTo(points[1].x, points[1].y)
                    lineTo(points[2].x, points[2].y)
                    lineTo(points[3].x, points[3].y)
                    close()
                }
                drawPath(path, Color.Cyan.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
                
                points.forEach { point ->
                    drawCircle(Color.Cyan, radius = 8.dp.toPx(), center = point)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = point)
                }
            }
        }
    }
}
