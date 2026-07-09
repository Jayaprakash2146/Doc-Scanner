package com.docscanner.app.scanner.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.docscanner.app.domain.model.ImageAdjustment
import com.docscanner.app.scanner.opencv.OpenCvBootstrap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object ImageAdjustmentProcessor {

    fun apply(base: Bitmap, adjustments: Map<ImageAdjustment, Int>): Bitmap {
        if (adjustments.values.all { it == 0 }) return base
        var result = base
        val brightness = adjustments[ImageAdjustment.BRIGHTNESS] ?: 0
        val contrast = adjustments[ImageAdjustment.CONTRAST] ?: 0
        val saturation = adjustments[ImageAdjustment.SATURATION] ?: 0
        val warmth = adjustments[ImageAdjustment.WARMTH] ?: 0
        val exposure = adjustments[ImageAdjustment.EXPOSURE] ?: 0
        val highlights = adjustments[ImageAdjustment.HIGHLIGHTS] ?: 0
        val shadows = adjustments[ImageAdjustment.SHADOWS] ?: 0
        val sharpen = adjustments[ImageAdjustment.SHARPEN] ?: 0

        val combined = ColorMatrix()
        if (brightness != 0 || exposure != 0) {
            val b = 1f + (brightness + exposure * 0.5f) / 200f
            combined.postConcat(ColorMatrix(floatArrayOf(
                b, 0f, 0f, 0f, (brightness + exposure) * 0.6f,
                0f, b, 0f, 0f, (brightness + exposure) * 0.6f,
                0f, 0f, b, 0f, (brightness + exposure) * 0.6f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        if (contrast != 0) {
            val scale = 1f + contrast / 100f
            val translate = (1f - scale) * 128f
            combined.postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        if (saturation != 0) {
            val sat = 1f + saturation / 100f
            val cm = ColorMatrix()
            cm.setSaturation(sat)
            combined.postConcat(cm)
        }
        if (warmth != 0) {
            val w = warmth / 200f
            combined.postConcat(ColorMatrix(floatArrayOf(
                1f + w, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - w, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        if (highlights != 0 || shadows != 0) {
            val h = highlights / 300f
            val s = shadows / 300f
            combined.postConcat(ColorMatrix(floatArrayOf(
                1f + h, 0f, 0f, 0f, s * 30f,
                0f, 1f + h, 0f, 0f, s * 30f,
                0f, 0f, 1f + h, 0f, s * 30f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        result = applyColorMatrix(base, combined)
        if (sharpen != 0 && OpenCvBootstrap.init()) {
            result = applySharpen(result, sharpen)
        }
        return result
    }

    private fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        if (source !== out && !source.isRecycled) {
            // caller owns source
        }
        return out
    }

    private fun applySharpen(bitmap: Bitmap, amount: Int): Bitmap {
        val src = Mat()
        val dst = Mat()
        Utils.bitmapToMat(bitmap, src)
        val blurred = Mat()
        Imgproc.GaussianBlur(src, blurred, org.opencv.core.Size(0.0, 0.0), 3.0)
        val strength = amount / 100.0
        org.opencv.core.Core.addWeighted(src, 1.0 + strength, blurred, -strength, 0.0, dst)
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, out)
        src.release()
        dst.release()
        blurred.release()
        if (bitmap !== out) bitmap.recycle()
        return out
    }
}
