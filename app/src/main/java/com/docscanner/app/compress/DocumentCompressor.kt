package com.docscanner.app.compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docscanner.app.export.CompressionResult
import com.docscanner.app.export.FileCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentCompressor(context: Context) {

    private val appContext = context.applicationContext
    private val fileCompressor = FileCompressor(appContext)
    private val workDir = File(appContext.cacheDir, "compress").apply { mkdirs() }

    suspend fun compress(
        sourceUri: Uri,
        qualityPercent: Int
    ): CompressionResult = withContext(Dispatchers.IO) {
        val input = copyUriToCache(sourceUri)
        val ext = input.extension.lowercase()
        val output = File(workDir, "${input.nameWithoutExtension}_compressed.$ext")
        when (ext) {
            "pdf" -> compressPdf(input, qualityPercent, output)
            "jpg", "jpeg" -> fileCompressor.compressJpeg(input, qualityPercent, output)
            "png" -> compressPng(input, qualityPercent, output)
            else -> throw IllegalArgumentException("Unsupported file type: .$ext")
        }
    }

    private suspend fun compressPdf(
        source: File,
        qualityPercent: Int,
        output: File
    ): CompressionResult {
        val bitmaps = renderPdfPages(source)
        if (bitmaps.isEmpty()) error("PDF has no pages")
        return try {
            fileCompressor.compressPdfFromBitmaps(bitmaps, qualityPercent, output)
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun compressPng(
        source: File,
        qualityPercent: Int,
        output: File
    ): CompressionResult {
        val originalSize = source.length()
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("Could not read image")
        val outFile = if (qualityPercent < 90) {
            File(workDir, "${source.nameWithoutExtension}_compressed.jpg").also { jpg ->
                FileOutputStream(jpg).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(30, 95), out)
                }
            }
        } else {
            FileOutputStream(output).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            output
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        return CompressionResult(
            file = outFile,
            originalSizeBytes = originalSize,
            compressedSizeBytes = outFile.length(),
            qualityPercent = qualityPercent
        )
    }

    private fun renderPdfPages(pdfFile: File): List<Bitmap> {
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { renderer ->
            buildList {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    add(bitmap)
                }
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val name = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
        val dest = File(workDir, name)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: error("Could not read selected file")
        return dest
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = appContext.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            return if (index >= 0) it.getString(index) else null
        }
    }
}
