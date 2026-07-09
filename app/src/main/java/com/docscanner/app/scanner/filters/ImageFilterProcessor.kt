package com.docscanner.app.scanner.filters

import android.graphics.Bitmap
import com.docscanner.app.domain.model.ScanFilter
import com.docscanner.app.scanner.opencv.BitmapMatUtils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class ImageFilterProcessor {

    fun apply(bitmap: Bitmap, filter: ScanFilter): Bitmap {
        require(!bitmap.isRecycled) { "Cannot filter a recycled bitmap" }
        if (filter == ScanFilter.ORIGINAL) {
            return BitmapMatUtils.toEditableBitmap(bitmap).let { editable ->
                if (editable === bitmap) editable.copy(Bitmap.Config.ARGB_8888, true)!! else editable
            }
        }

        val src = BitmapMatUtils.bitmapToMat(bitmap)
        try {
            val output = when (filter) {
                ScanFilter.GRAYSCALE -> toGrayscale(src)
                ScanFilter.BLACK_WHITE -> toBlackWhite(src)
                ScanFilter.MAGIC_COLOR -> toMagicColor(src)
                ScanFilter.SHARPEN -> sharpen(src)
                ScanFilter.ORIGINAL -> src
            }
            val result = BitmapMatUtils.matToBitmap(output)
            if (output !== src) output.release()
            return result
        } finally {
            src.release()
        }
    }

    private fun toGrayscale(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val out = Mat()
        Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2BGR)
        gray.release()
        return out
    }

    private fun toBlackWhite(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            15,
            4.0
        )
        val out = Mat()
        Imgproc.cvtColor(binary, out, Imgproc.COLOR_GRAY2BGR)
        gray.release()
        binary.release()
        return out
    }

    /**
     * Adobe Scan–style: denoise, CLAHE, saturation + contrast boost, warm white balance.
     */
    private fun toMagicColor(src: Mat): Mat {
        val denoised = Mat()
        Imgproc.bilateralFilter(src, denoised, 7, 60.0, 60.0)

        val lab = Mat()
        Imgproc.cvtColor(denoised, lab, Imgproc.COLOR_BGR2Lab)
        val labChannels = ArrayList<Mat>()
        Core.split(lab, labChannels)
        val clahe = Imgproc.createCLAHE(3.5, Size(8.0, 8.0))
        clahe.apply(labChannels[0], labChannels[0])
        labChannels[0].convertTo(labChannels[0], -1, 1.08, 8.0)
        Core.merge(labChannels, lab)
        val fromLab = Mat()
        Imgproc.cvtColor(lab, fromLab, Imgproc.COLOR_Lab2BGR)

        val hsv = Mat()
        Imgproc.cvtColor(fromLab, hsv, Imgproc.COLOR_BGR2HSV)
        val hsvCh = ArrayList<Mat>()
        Core.split(hsv, hsvCh)
        hsvCh[1].convertTo(hsvCh[1], -1, 1.35, 0.0)
        hsvCh[2].convertTo(hsvCh[2], -1, 1.12, 12.0)
        Core.merge(hsvCh, hsv)
        val saturated = Mat()
        Imgproc.cvtColor(hsv, saturated, Imgproc.COLOR_HSV2BGR)

        val result = Mat()
        Core.addWeighted(saturated, 0.92, denoised, 0.08, 0.0, result)

        denoised.release()
        lab.release()
        labChannels.forEach { it.release() }
        fromLab.release()
        hsv.release()
        hsvCh.forEach { it.release() }
        saturated.release()
        return result
    }

    /** Strong unsharp mask — clearly sharper than Original / Magic Color. */
    private fun sharpen(src: Mat): Mat {
        val blurred = Mat()
        Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 1.8)

        val unsharp = Mat()
        Core.addWeighted(src, 2.35, blurred, -1.35, 0.0, unsharp)
        blurred.release()

        val laplacian = Mat()
        Imgproc.Laplacian(src, laplacian, CvType.CV_16S, 3, 1.0, 0.0)
        val edges = Mat()
        Core.convertScaleAbs(laplacian, edges)
        laplacian.release()

        val edgeBoost = Mat()
        Core.addWeighted(unsharp, 1.0, edges, 0.28, 0.0, edgeBoost)
        edges.release()
        unsharp.release()
        return edgeBoost
    }
}
