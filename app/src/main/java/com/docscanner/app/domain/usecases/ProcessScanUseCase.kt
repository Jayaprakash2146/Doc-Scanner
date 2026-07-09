package com.docscanner.app.domain.usecases

import android.graphics.Bitmap
import com.docscanner.app.data.repository.ScanRepository
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.domain.model.ScanFilter

class ProcessScanUseCase(private val repository: ScanRepository) {

    suspend fun detectCorners(bitmap: Bitmap): List<Point2D>? =
        repository.detectCorners(bitmap)

    suspend fun detectCornersAggressive(bitmap: Bitmap): List<Point2D>? =
        repository.detectCornersAggressive(bitmap)

    suspend fun crop(bitmap: Bitmap, corners: List<Point2D>): Bitmap =
        repository.cropDocument(bitmap, corners)

    suspend fun applyFilter(bitmap: Bitmap, filter: ScanFilter): Bitmap =
        repository.applyFilter(bitmap, filter)

    suspend fun saveJpeg(bitmap: Bitmap) = repository.saveJpeg(bitmap)

    suspend fun savePdf(bitmaps: List<Bitmap>) = repository.savePdf(bitmaps)
}
