package com.docscanner.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.docscanner.app.scanner.pdf.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(private val context: Context) {

    private val pdfGenerator = PdfGenerator(context)
    private val fileCompressor = FileCompressor(context)
    private val folderName = "DocumentsScanner"

    suspend fun savePdfToDownloads(bitmaps: List<Bitmap>, jpegQuality: Float = 0.82f): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = "SCAN_${timestamp()}.pdf"
                val cacheFile = File(context.cacheDir, fileName)
                pdfGenerator.createPdf(bitmaps, cacheFile, jpegQuality)
                copyToDownloads(cacheFile, fileName, "application/pdf")
            }.getOrElse {
                Log.e(TAG, "savePdf failed", it)
                null
            }
        }

    suspend fun compressAndSavePdfToDownloads(
        bitmaps: List<Bitmap>,
        qualityPercent: Int
    ): CompressionResult? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "SCAN_${timestamp()}_compressed.pdf"
            val cacheFile = File(context.cacheDir, fileName)
            val result = fileCompressor.compressPdfFromBitmaps(bitmaps, qualityPercent, cacheFile)
            val saved = copyToDownloads(result.file, fileName, "application/pdf")
            result.copy(file = saved ?: result.file)
        }.getOrElse {
            Log.e(TAG, "compress pdf failed", it)
            null
        }
    }

    suspend fun compressAndSaveJpegsToDownloads(
        bitmaps: List<Bitmap>,
        qualityPercent: Int
    ): List<CompressionResult> = withContext(Dispatchers.IO) {
        runCatching {
            bitmaps.mapIndexed { index, bitmap ->
                val fileName = "SCAN_${timestamp()}_${index + 1}.jpg"
                val cacheFile = File(context.cacheDir, fileName)
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        qualityPercent.coerceIn(30, 95),
                        out
                    )
                }
                val baseline = File(context.cacheDir, "base_$fileName")
                FileOutputStream(baseline).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                val saved = copyToDownloads(cacheFile, fileName, "image/jpeg") ?: cacheFile
                CompressionResult(
                    file = saved,
                    originalSizeBytes = baseline.length(),
                    compressedSizeBytes = saved.length(),
                    qualityPercent = qualityPercent
                ).also { baseline.delete() }
            }
        }.getOrElse {
            Log.e(TAG, "compress jpg failed", it)
            emptyList()
        }
    }

    suspend fun saveJpegsToDownloads(bitmaps: List<Bitmap>, quality: Int = 88): List<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                bitmaps.mapIndexed { index, bitmap ->
                    val fileName = "SCAN_${timestamp()}_${index + 1}.jpg"
                    val cacheFile = File(context.cacheDir, fileName)
                    FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    copyToDownloads(cacheFile, fileName, "image/jpeg") ?: cacheFile
                }
            }.getOrElse { emptyList() }
        }

    private fun copyToDownloads(source: File, fileName: String, mimeType: String): File? {
        if (!source.exists() || source.length() == 0L) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().buffered().use { it.copyTo(out) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                File(context.filesDir, "scans").apply { mkdirs() }.let { dir ->
                    File(dir, fileName).also { dest ->
                        source.copyTo(dest, overwrite = true)
                    }
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    folderName
                )
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, fileName)
                source.copyTo(dest, overwrite = true)
                dest
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyToDownloads", e)
            File(context.filesDir, "scans").apply { mkdirs() }.let { dir ->
                File(dir, fileName).also {
                    runCatching { source.copyTo(it, overwrite = true) }
                }
            }
        }
    }

    suspend fun saveConvertedFileToDownloads(
        source: File,
        extension: String,
        mimeType: String
    ): File? = withContext(Dispatchers.IO) {
        val fileName = "CONVERT_${timestamp()}.$extension"
        copyToDownloads(source, fileName, mimeType)
    }

    fun formatSize(bytes: Long) = fileCompressor.formatSize(bytes)

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        private const val TAG = "ExportManager"
    }
}
