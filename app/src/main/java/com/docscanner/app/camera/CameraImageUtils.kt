package com.docscanner.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.docscanner.app.scanner.opencv.BitmapMatUtils
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object CameraImageUtils {

    /** Full-resolution capture from ImageCapture (JPEG). */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val rotation = image.imageInfo.rotationDegrees
        var bitmap: Bitmap? = null

        if (image.format == ImageFormat.JPEG || image.planes.size == 1) {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.rewind()
            buffer.get(bytes)
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        if (bitmap == null) {
            bitmap = yuvToBitmap(image)
        }
        if (bitmap == null) {
            bitmap = rgbaToBitmap(image)
        }
        bitmap ?: return null

        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    private fun rgbaToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            val buffer = image.planes[0].buffer
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap? {
        if (image.planes.size < 3) return null
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
