package com.docscanner.app.domain.model

data class RecentScanItem(
    val id: String,
    val title: String,
    val thumbnailPath: String,
    val pageCount: Int,
    val createdAt: Long,
    val pdfPath: String? = null
)
