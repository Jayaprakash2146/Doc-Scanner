package com.docscanner.app.presentation

import android.graphics.Bitmap
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.domain.model.ScanFilter

data class ScanSessionState(
    val capturedBitmaps: List<Bitmap> = emptyList(),
    val currentCapturedBitmap: Bitmap? = null,
    val croppedBitmap: Bitmap? = null,
    val displayBitmap: Bitmap? = null,
    val corners: List<Point2D> = emptyList(),
    val selectedFilter: ScanFilter = ScanFilter.ORIGINAL,
    val isProcessing: Boolean = false,
    val message: String? = null,
    val lastSavedPath: String? = null,
    val showMultiPageOption: Boolean = false,
    val isAdjustingEdges: Boolean = false,
    val tempCorners: List<Point2D> = emptyList()
)
