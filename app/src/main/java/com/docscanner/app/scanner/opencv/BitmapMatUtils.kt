package com.docscanner.app.scanner.opencv

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object BitmapMatUtils {

    /** Safe mutable ARGB copy for OpenCV and Bitmap.copy (avoids HARDWARE / recycled crashes). */
    fun toEditableBitmap(bitmap: Bitmap): Bitmap {
        if (!bitmap.isRecycled &&
            bitmap.config == Bitmap.Config.ARGB_8888 &&
            bitmap.isMutable
        ) {
            return bitmap
        }
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (copy != null) return copy
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(bitmap, 0f, 0f, null)
        return out
    }

    fun bitmapToMat(bitmap: Bitmap): Mat {
        val editable = toEditableBitmap(bitmap)
        val mat = Mat()
        Utils.bitmapToMat(editable, mat)
        if (editable !== bitmap) editable.recycle()
        if (mat.channels() == 4) {
            val bgr = Mat()
            Imgproc.cvtColor(mat, bgr, Imgproc.COLOR_RGBA2BGR)
            mat.release()
            return bgr
        }
        return mat
    }

    fun matToBitmap(mat: Mat): Bitmap {
        val output = if (mat.channels() == 1) {
            val rgba = Mat()
            Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_GRAY2RGBA)
            rgba
        } else {
            val rgba = Mat()
            Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
            rgba
        }
        val bitmap = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(output, bitmap)
        output.release()
        return bitmap
    }

    fun downscale(bitmap: Bitmap, maxDimension: Int = 1600): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / largest
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
