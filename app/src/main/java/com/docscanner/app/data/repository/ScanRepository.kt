package com.docscanner.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.domain.model.ScanFilter
import com.docscanner.app.scanner.filters.ImageFilterProcessor
import com.docscanner.app.scanner.opencv.DocumentEdgeDetector
import com.docscanner.app.scanner.pdf.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanRepository(
    private val context: Context,
    private val edgeDetector: DocumentEdgeDetector = DocumentEdgeDetector(),
    private val filterProcessor: ImageFilterProcessor = ImageFilterProcessor(),
    private val pdfGenerator: PdfGenerator = PdfGenerator(context)
) {

    private val scansDir: File
        get() = File(context.filesDir, "scans").apply { mkdirs() }

    suspend fun detectCorners(bitmap: Bitmap): List<Point2D>? = withContext(Dispatchers.Default) {
        edgeDetector.detectCorners(bitmap)
    }

    suspend fun detectCornersAggressive(bitmap: Bitmap): List<Point2D>? =
        withContext(Dispatchers.Default) {
            edgeDetector.detectCornersAggressive(bitmap)
        }

    suspend fun cropDocument(bitmap: Bitmap, corners: List<Point2D>): Bitmap =
        withContext(Dispatchers.Default) {
            edgeDetector.warpPerspective(bitmap, corners)
        }

    suspend fun applyFilter(bitmap: Bitmap, filter: ScanFilter): Bitmap =
        withContext(Dispatchers.Default) {
            filterProcessor.apply(bitmap, filter)
        }

    suspend fun saveJpeg(bitmap: Bitmap, namePrefix: String = "scan"): File =
        withContext(Dispatchers.IO) {
            val file = File(scansDir, "${namePrefix}_${timestamp()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file
        }

    suspend fun savePdf(bitmaps: List<Bitmap>, namePrefix: String = "SCAN"): File =
        withContext(Dispatchers.IO) {
            val file = File(scansDir, "${namePrefix}_${timestamp()}.pdf")
            pdfGenerator.createPdf(bitmaps, file)
            file
        }

    fun getShareUri(file: File): Uri {
        require(file.exists() && file.canRead()) { "Export file not found: ${file.absolutePath}" }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }
}
