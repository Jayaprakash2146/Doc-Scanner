package com.docscanner.app.domain.model

import android.graphics.Bitmap
import com.docscanner.app.domain.model.ImageAdjustment.Companion.defaults
import java.util.UUID

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    var originalBitmap: Bitmap,
    var croppedBitmap: Bitmap? = null,
    var displayBitmap: Bitmap? = null,
    var corners: List<Point2D> = emptyList(),
    var filter: ScanFilter = ScanFilter.ORIGINAL,
    var rotationDegrees: Int = 0,
    var adjustments: MutableMap<ImageAdjustment, Int> = defaults().toMutableMap()
) {
    fun workingBitmap(): Bitmap = displayBitmap ?: croppedBitmap ?: originalBitmap
}
