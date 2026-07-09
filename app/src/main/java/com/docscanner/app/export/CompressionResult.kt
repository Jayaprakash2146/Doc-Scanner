package com.docscanner.app.export

import java.io.File

data class CompressionResult(
    val file: File,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val qualityPercent: Int
) {
    val savedBytes: Long get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0)
    val savedPercent: Int
        get() = if (originalSizeBytes <= 0) 0
        else ((savedBytes * 100) / originalSizeBytes).toInt()
}
