package com.docscanner.app.domain.model

import android.graphics.Bitmap

/** State for post-capture edge adjust + scan preview ([h4rz/Android-Document-Scanner](https://github.com/h4rz/Android-Document-Scanner) flow). */
data class CropAdjustState(
    val captureBitmap: Bitmap,
    val corners: List<Point2D>,
    val phase: CaptureFlowPhase = CaptureFlowPhase.ADJUST_EDGES,
    val croppedBitmap: Bitmap? = null,
    val scannedPreview: Bitmap? = null
)
