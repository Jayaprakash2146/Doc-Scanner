package com.docscanner.app.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docscanner.app.scanner.pdf.PdfGenerator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileConverter(context: Context) {

    private val appContext = context.applicationContext
    private val pdfGenerator = PdfGenerator(appContext)
    private val outputDir = File(appContext.cacheDir, "conversions").apply { mkdirs() }

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun convert(type: ConversionType, sourceUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val input = copyUriToCache(sourceUri)
            when (type) {
                ConversionType.PDF_TO_WORD -> pdfToWord(input)
                ConversionType.WORD_TO_PDF -> wordToPdf(input)
                ConversionType.PPT_TO_PDF -> pptToPdf(input)
                ConversionType.PDF_TO_JPEG -> pdfToImage(input, Bitmap.CompressFormat.JPEG, "jpg")
                ConversionType.PDF_TO_PNG -> pdfToImage(input, Bitmap.CompressFormat.PNG, "png")
                ConversionType.JPEG_TO_PNG,
                ConversionType.JPG_TO_PNG -> imageToImage(input, Bitmap.CompressFormat.PNG, "png")
                ConversionType.PNG_TO_JPEG -> imageToImage(input, Bitmap.CompressFormat.JPEG, "jpg")
                ConversionType.IMAGE_TO_PDF -> imageToPdf(input)
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val name = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
        val dest = File(outputDir, name)
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

    private fun pdfToWord(pdfFile: File): File {
        val document = PDDocument.load(pdfFile)
        val text = try {
            PDFTextStripper().getText(document)
        } finally {
            document.close()
        }
        val out = File(outputDir, pdfFile.nameWithoutExtension + ".docx")
        writeSimpleDocx(out, text.ifBlank { " " })
        return out
    }

    private fun wordToPdf(wordFile: File): File {
        val text = when (wordFile.extension.lowercase()) {
            "docx" -> extractDocxText(wordFile)
            else -> wordFile.readText()
        }
        return textToPdf(text, wordFile.nameWithoutExtension + ".pdf")
    }

    private fun pptToPdf(pptFile: File): File {
        val slides = when (pptFile.extension.lowercase()) {
            "pptx" -> extractPptxSlideTexts(pptFile)
            else -> listOf(pptFile.readText())
        }
        val out = File(outputDir, pptFile.nameWithoutExtension + ".pdf")
        val document = PDDocument()
        try {
            val font = PDType1Font.HELVETICA
            slides.forEachIndexed { index, slideText ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(font, 12f)
                    stream.newLineAtOffset(40f, page.mediaBox.height - 60f)
                    val header = "Slide ${index + 1}"
                    stream.showText(sanitizePdfText(header))
                    stream.newLineAtOffset(0f, -24f)
                    wrapText(slideText).take(42).forEach { line ->
                        stream.showText(sanitizePdfText(line))
                        stream.newLineAtOffset(0f, -16f)
                    }
                    stream.endText()
                }
            }
            if (slides.isEmpty()) {
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
            }
            document.save(out)
        } finally {
            document.close()
        }
        return out
    }

    private fun pdfToImage(pdfFile: File, format: Bitmap.CompressFormat, ext: String): File {
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount == 0) error("PDF has no pages")
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            val out = File(outputDir, "${pdfFile.nameWithoutExtension}.$ext")
            FileOutputStream(out).use { stream ->
                val quality = if (format == Bitmap.CompressFormat.JPEG) 92 else 100
                bitmap.compress(format, quality, stream)
            }
            bitmap.recycle()
            return out
        }
    }

    private fun imageToImage(input: File, format: Bitmap.CompressFormat, ext: String): File {
        val bitmap = BitmapFactory.decodeFile(input.absolutePath)
            ?: error("Unsupported image file")
        val out = File(outputDir, "${input.nameWithoutExtension}.$ext")
        FileOutputStream(out).use { stream ->
            val quality = if (format == Bitmap.CompressFormat.JPEG) 92 else 100
            bitmap.compress(format, quality, stream)
        }
        bitmap.recycle()
        return out
    }

    private fun imageToPdf(imageFile: File): File {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: error("Unsupported image file")
        val out = File(outputDir, "${imageFile.nameWithoutExtension}.pdf")
        pdfGenerator.createPdf(listOf(bitmap), out)
        bitmap.recycle()
        return out
    }

    private fun textToPdf(text: String, fileName: String): File {
        val out = File(outputDir, fileName)
        val document = PDDocument()
        try {
            val font = PDType1Font.HELVETICA
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)
            var stream = PDPageContentStream(document, page)
            stream.beginText()
            stream.setFont(font, 11f)
            var y = page.mediaBox.height - 48f
            stream.newLineAtOffset(40f, y)
            wrapText(text).forEach { line ->
                if (y < 48f) {
                    stream.endText()
                    stream.close()
                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    stream = PDPageContentStream(document, page)
                    stream.beginText()
                    stream.setFont(font, 11f)
                    y = page.mediaBox.height - 48f
                    stream.newLineAtOffset(40f, y)
                }
                stream.showText(sanitizePdfText(line))
                stream.newLineAtOffset(0f, -14f)
                y -= 14f
            }
            stream.endText()
            stream.close()
            document.save(out)
        } finally {
            document.close()
        }
        return out
    }

    private fun extractDocxText(docx: File): String {
        ZipInputStream(docx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return parseXmlText(zip.readBytes().decodeToString())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return ""
    }

    private fun extractPptxSlideTexts(pptx: File): List<String> {
        val slides = mutableListOf<String>()
        ZipInputStream(pptx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    slides.add(parseXmlText(zip.readBytes().decodeToString()))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return slides.sorted()
    }

    private fun parseXmlText(xml: String): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        val builder = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) {
                builder.append(parser.text).append(' ')
            }
            event = parser.next()
        }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun writeSimpleDocx(output: File, text: String) {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                ${wrapText(text).joinToString("\n") { line ->
            """<w:p><w:r><w:t>${escapeXml(line)}</w:t></w:r></w:p>"""
        }}
              </w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(output)).use { zip ->
            zip. putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
    }

    private fun wrapText(text: String, maxChars: Int = 90): List<String> {
        if (text.isBlank()) return listOf(" ")
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.length + word.length + 1 > maxChars) {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(" ") }
    }

    private fun sanitizePdfText(text: String): String {
        return text.replace(Regex("[^\\x20-\\x7E]"), " ").take(180)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
