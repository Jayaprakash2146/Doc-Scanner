package com.docscanner.app.convert

import androidx.annotation.DrawableRes
import com.docscanner.app.R

enum class ConversionType(
    val title: String,
    @DrawableRes val iconRes: Int,
    val sourceExtensions: Set<String>,
    val outputExtension: String,
    val mimeType: String
) {
    PDF_TO_WORD(
        "PDF → Word",
        R.drawable.pdf_to_word,
        setOf("pdf"),
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ),
    WORD_TO_PDF(
        "Word → PDF",
        R.drawable.word_to_pdf,
        setOf("doc", "docx"),
        "pdf",
        "application/pdf"
    ),
    PPT_TO_PDF(
        "PowerPoint → PDF",
        R.drawable.ppt_to_pdf,
        setOf("ppt", "pptx"),
        "pdf",
        "application/pdf"
    ),
    PDF_TO_JPEG(
        "PDF → JPEG",
        R.drawable.pdf_to_jpeg,
        setOf("pdf"),
        "jpg",
        "image/jpeg"
    ),
    PDF_TO_PNG(
        "PDF → PNG",
        R.drawable.pdf_to_png,
        setOf("pdf"),
        "png",
        "image/png"
    ),
    JPEG_TO_PNG(
        "JPEG → PNG",
        R.drawable.jpeg_to_png,
        setOf("jpg", "jpeg"),
        "png",
        "image/png"
    ),
    PNG_TO_JPEG(
        "PNG → JPEG",
        R.drawable.png_to_jpeg,
        setOf("png"),
        "jpg",
        "image/jpeg"
    ),
    JPG_TO_PNG(
        "JPG → PNG",
        R.drawable.jpg_to_png,
        setOf("jpg"),
        "png",
        "image/png"
    ),
    IMAGE_TO_PDF(
        "Image → PDF",
        R.drawable.image_to_pdf,
        setOf("jpg", "jpeg", "png"),
        "pdf",
        "application/pdf"
    );

    companion object {
        fun all(): List<ConversionType> = entries.toList()
    }
}
