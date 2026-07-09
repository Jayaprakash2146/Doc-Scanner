package com.docscanner.app.domain.model

import android.graphics.Bitmap

data class ScanDocument(
    val originalBitmap: Bitmap,
    val croppedBitmap: Bitmap? = null,
    val corners: List<Point2D> = emptyList(),
    val activeFilter: ScanFilter = ScanFilter.ORIGINAL
)
