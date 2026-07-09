package com.docscanner.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.docscanner.app.scanner.pdf.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileCompressor(context: Context) {

    private val pdfGenerator = PdfGenerator(context)

    suspend fun compressPdfFromBitmaps(
        bitmaps: List<Bitmap>,
        qualityPercent: Int,
        outputFile: File
    ): CompressionResult = withContext(Dispatchers.IO) {
        val highQuality = qualityPercent.coerceIn(30, 95)
        val baseline = File(outputFile.parentFile, "baseline_${outputFile.name}")
        pdfGenerator.createPdf(bitmaps, baseline, 0.92f)
        val originalSize = baseline.length()
        pdfGenerator.createPdf(bitmaps, outputFile, highQuality / 100f)
        val compressedSize = outputFile.length()
        baseline.delete()
        CompressionResult(
            file = outputFile,
            originalSizeBytes = originalSize,
            compressedSizeBytes = compressedSize,
            qualityPercent = highQuality
        )
    }

    suspend fun compressPdfFile(
        source: File,
        qualityPercent: Int,
        outputFile: File
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalSize = source.length()
        source.copyTo(outputFile, overwrite = true)
        CompressionResult(
            file = outputFile,
            originalSizeBytes = originalSize,
            compressedSizeBytes = outputFile.length(),
            qualityPercent = qualityPercent
        )
    }

    suspend fun compressJpeg(
        source: File,
        qualityPercent: Int,
        outputFile: File
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalSize = source.length()
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: return@withContext CompressionResult(source, originalSize, originalSize, qualityPercent)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                qualityPercent.coerceIn(30, 95),
                out
            )
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        CompressionResult(
            file = outputFile,
            originalSizeBytes = originalSize,
            compressedSizeBytes = outputFile.length(),
            qualityPercent = qualityPercent
        )
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
