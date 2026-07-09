package com.docscanner.app.scanner.opencv

import android.graphics.Bitmap
import com.docscanner.app.domain.model.Point2D
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class DocumentEdgeDetector {

    fun detectCorners(bitmap: Bitmap, realtime: Boolean = false): List<Point2D>? {
        if (!realtime) {
            return detectCornersAggressive(bitmap)
        }
        val maxDim = 800
        val scaled = BitmapMatUtils.downscale(bitmap, maxDim)
        val src = BitmapMatUtils.bitmapToMat(scaled)
        if (scaled !== bitmap) scaled.recycle()

        var result = findQuad(src, 0.03, cannyLow = 40.0, cannyHigh = 140.0, epsilon = 0.02)
        if (result == null) result = findQuad(src, 0.025, cannyLow = 25.0, cannyHigh = 110.0, epsilon = 0.03)
        if (result == null) result = findQuadAdaptive(src, 0.025)
        if (result == null) result = findQuad(src, 0.02, cannyLow = 55.0, cannyHigh = 160.0, epsilon = 0.04)
        src.release()
        return scaleCornersToSource(result, scaled.width, scaled.height, bitmap.width, bitmap.height)
    }

    /** Aggressive detection for editor "Auto" crop — finds paper edges even with more frame visible. */
    fun detectCornersAggressive(bitmap: Bitmap): List<Point2D>? {
        val scaled = BitmapMatUtils.downscale(bitmap, 1400)
        val src = BitmapMatUtils.bitmapToMat(scaled)
        if (scaled !== bitmap) scaled.recycle()
        var result = findQuad(src, 0.03, 40.0, 140.0, 0.02)
        if (result == null) result = findQuad(src, 0.03, 30.0, 100.0, 0.03)
        if (result == null) result = findQuad(src, 0.02, 50.0, 150.0, 0.04)
        if (result == null) result = findQuadAdaptive(src, 0.02)
        src.release()
        return scaleCornersToSource(result, scaled.width, scaled.height, bitmap.width, bitmap.height)
    }

    private fun scaleCornersToSource(
        corners: List<Point2D>?,
        fromWidth: Int,
        fromHeight: Int,
        toWidth: Int,
        toHeight: Int
    ): List<Point2D>? {
        if (corners == null) return null
        if (fromWidth == toWidth && fromHeight == toHeight) return corners
        val scaleX = toWidth.toFloat() / fromWidth
        val scaleY = toHeight.toFloat() / fromHeight
        return corners.map { Point2D(it.x * scaleX, it.y * scaleY) }
    }

    private fun findQuad(
        src: Mat,
        minAreaRatio: Double,
        cannyLow: Double,
        cannyHigh: Double,
        epsilon: Double
    ): List<Point2D>? {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, cannyLow, cannyHigh)
        Imgproc.dilate(edges, edges, Mat())

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            edges,
            contours,
            Mat(),
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val imageArea = src.rows() * src.cols()
        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0

        for (contour in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, epsilon * peri, true)

            val count = approx.total().toInt()
            if (count in 4..6) {
                val area = abs(Imgproc.contourArea(approx))
                if (area > imageArea * minAreaRatio && area > bestArea) {
                    bestArea = area
                    bestQuad = approx
                }
            }
            contour.release()
        }

        gray.release()
        edges.release()

        if (bestQuad == null) return null
        val pts = bestQuad.toArray().take(4).map { Point2D(it.x.toFloat(), it.y.toFloat()) }
        bestQuad.release()
        return if (pts.size == 4) orderCorners(pts) else null
    }

    private fun findQuadAdaptive(src: Mat, minAreaRatio: Double): List<Point2D>? {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11, 2.0
        )
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val imageArea = src.rows() * src.cols()
        var best: List<Point2D>? = null
        var bestArea = 0.0
        for (contour in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.04 * peri, true)
            if (approx.total() >= 4) {
                val area = abs(Imgproc.contourArea(approx))
                if (area > imageArea * minAreaRatio && area > bestArea) {
                    bestArea = area
                    val pts = approx.toArray().take(4).map { Point2D(it.x.toFloat(), it.y.toFloat()) }
                    if (pts.size == 4) best = orderCorners(pts)
                }
            }
            contour.release()
        }
        gray.release()
        binary.release()
        return best
    }

    fun warpPerspective(bitmap: Bitmap, corners: List<Point2D>): Bitmap {
        require(corners.size == 4) { "Four corners required" }

        val src = BitmapMatUtils.bitmapToMat(bitmap)
        val ordered = orderCorners(corners)

        val widthTop = distance(ordered[0], ordered[1])
        val widthBottom = distance(ordered[3], ordered[2])
        val maxWidth = max(widthTop, widthBottom).toInt().coerceAtLeast(1)

        val heightLeft = distance(ordered[0], ordered[3])
        val heightRight = distance(ordered[1], ordered[2])
        val maxHeight = max(heightLeft, heightRight).toInt().coerceAtLeast(1)

        val srcPoints = MatOfPoint2f(
            Point(ordered[0].x.toDouble(), ordered[0].y.toDouble()),
            Point(ordered[1].x.toDouble(), ordered[1].y.toDouble()),
            Point(ordered[2].x.toDouble(), ordered[2].y.toDouble()),
            Point(ordered[3].x.toDouble(), ordered[3].y.toDouble())
        )

        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth - 1.0, 0.0),
            Point(maxWidth - 1.0, maxHeight - 1.0),
            Point(0.0, maxHeight - 1.0)
        )

        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val output = Mat()
        Imgproc.warpPerspective(
            src,
            output,
            transform,
            Size(maxWidth.toDouble(), maxHeight.toDouble())
        )

        val result = BitmapMatUtils.matToBitmap(output)

        src.release()
        output.release()
        transform.release()
        srcPoints.release()
        dstPoints.release()

        return result
    }

    private fun orderCorners(points: List<Point2D>): List<Point2D> {
        val sortedByY = points.sortedBy { it.y }
        val top = sortedByY.take(2).sortedBy { it.x }
        val bottom = sortedByY.takeLast(2).sortedBy { it.x }
        return listOf(top[0], top[1], bottom[1], bottom[0])
    }

    private fun distance(a: Point2D, b: Point2D): Double {
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
    }
}
