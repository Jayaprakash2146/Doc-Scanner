package com.docscanner.app.scanner.pdf

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream

class PdfGenerator(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun createPdf(
        bitmaps: List<Bitmap>,
        outputFile: File,
        jpegQuality: Float = 0.82f
    ): File {
        val quality = jpegQuality.coerceIn(0.3f, 0.95f)
        val document = PDDocument()
        try {
            bitmaps.forEach { bitmap ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                val image = JPEGFactory.createFromImage(document, bitmap, quality)
                val pageBox = page.mediaBox
                val scale = minOf(
                    pageBox.width / bitmap.width,
                    pageBox.height / bitmap.height
                )
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val offsetX = (pageBox.width - drawWidth) / 2f
                val offsetY = (pageBox.height - drawHeight) / 2f
                PDPageContentStream(document, page).use { content ->
                    content.drawImage(image, offsetX, offsetY, drawWidth, drawHeight)
                }
            }
            FileOutputStream(outputFile).use { out ->
                document.save(out)
            }
        } finally {
            document.close()
        }
        return outputFile
    }
}
