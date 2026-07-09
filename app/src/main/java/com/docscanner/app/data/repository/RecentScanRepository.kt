package com.docscanner.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.docscanner.app.domain.model.RecentScanItem
import com.docscanner.app.domain.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RecentScanRepository(private val context: Context) {

    private val rootDir = File(context.filesDir, "scan_history").apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")

    suspend fun getAll(): List<RecentScanItem> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        RecentScanItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            thumbnailPath = obj.getString("thumbnailPath"),
                            pageCount = obj.getInt("pageCount"),
                            createdAt = obj.getLong("createdAt"),
                            pdfPath = obj.optString("pdfPath").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    suspend fun saveOrUpdateSession(
        existingId: String?,
        pages: List<ScanPage>,
        pdfFile: File? = null
    ): String = withContext(Dispatchers.IO) {
        val id = existingId ?: UUID.randomUUID().toString()
        val sessionDir = File(rootDir, id).apply { mkdirs() }
        val pagesDir = File(sessionDir, "pages").apply { mkdirs() }

        pages.forEachIndexed { index, page ->
            val file = File(pagesDir, "page_${index + 1}.jpg")
            FileOutputStream(file).use { out ->
                page.workingBitmap().compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        }

        val thumbFile = File(sessionDir, "thumb.jpg")
        FileOutputStream(thumbFile).use { out ->
            pages.first().workingBitmap().compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        pdfFile?.let { src ->
            if (src.exists()) {
                val dest = File(sessionDir, "export.pdf")
                src.inputStream().use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            }
        }

        val title = "Scan ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())}"
        val item = RecentScanItem(
            id = id,
            title = title,
            thumbnailPath = thumbFile.absolutePath,
            pageCount = pages.size,
            createdAt = System.currentTimeMillis(),
            pdfPath = pdfFile?.let { File(sessionDir, "export.pdf").absolutePath }
                ?: existingPdfPath(id)
        )

        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        list.add(0, item)
        writeIndex(list.take(40))
        id
    }

    suspend fun loadPageBitmaps(id: String): List<Bitmap> = withContext(Dispatchers.IO) {
        val pagesDir = File(rootDir, "$id/pages")
        if (!pagesDir.exists()) return@withContext emptyList()
        pagesDir.listFiles()
            ?.filter { it.extension.equals("jpg", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull { path ->
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(path.absolutePath)
                }.getOrNull()
            }
            .orEmpty()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(rootDir, id).deleteRecursively()
        val list = getAll().filterNot { it.id == id }
        writeIndex(list)
    }

    suspend fun rename(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val list = getAll().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@withContext
        list[index] = list[index].copy(title = newTitle.trim())
        writeIndex(list)
    }

    fun getShareablePdfFile(item: RecentScanItem): File? {
        val path = item.pdfPath ?: return null
        val file = File(path)
        return file.takeIf { it.exists() }
    }

    private fun existingPdfPath(id: String): String? {
        val pdf = File(rootDir, "$id/export.pdf")
        return pdf.absolutePath.takeIf { pdf.exists() }
    }

    private fun writeIndex(items: List<RecentScanItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("thumbnailPath", item.thumbnailPath)
                    put("pageCount", item.pageCount)
                    put("createdAt", item.createdAt)
                    put("pdfPath", item.pdfPath.orEmpty())
                }
            )
        }
        indexFile.writeText(array.toString())
    }
}
