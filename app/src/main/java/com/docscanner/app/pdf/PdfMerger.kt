package com.docscanner.app.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.io.File

class PdfMerger(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun merge(sources: List<File>, output: File): File {
        require(sources.size >= 2) { "At least two PDF files required" }
        val merger = PDFMergerUtility()
        merger.destinationFileName = output.absolutePath
        sources.forEach { file ->
            require(file.exists() && file.extension.equals("pdf", true)) {
                "Invalid PDF: ${file.name}"
            }
            merger.addSource(file)
        }
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        return output
    }
}
