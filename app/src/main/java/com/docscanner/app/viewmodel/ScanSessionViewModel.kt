package com.docscanner.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.data.repository.RecentScanRepository
import com.docscanner.app.data.repository.ScanRepository
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.domain.model.ScanFilter
import com.docscanner.app.domain.model.ScanPage
import com.docscanner.app.domain.usecases.ProcessScanUseCase
import com.docscanner.app.export.CompressionResult
import com.docscanner.app.export.ExportManager
import com.docscanner.app.domain.model.CaptureFlowPhase
import com.docscanner.app.domain.model.CropAdjustState
import com.docscanner.app.scanner.detection.DocumentDetectionResult
import com.docscanner.app.scanner.opencv.BitmapMatUtils
import com.docscanner.app.scanner.opencv.OpenCvBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScanUiState(
    val pages: List<ScanPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val isProcessing: Boolean = false,
    val message: String? = null,
    val lastExportFile: File? = null,
    val showMultiPageBar: Boolean = false,
    val pendingCapture: Bitmap? = null,
    val pendingCaptureIsCropped: Boolean = false,
    val liveDetection: DocumentDetectionResult? = null,
    val cropMode: Boolean = false,
    val lastCompression: CompressionResult? = null,
    val cropAdjust: CropAdjustState? = null,
    val previewToken: Int = 0
) {
    fun currentPage(): ScanPage? = pages.getOrNull(currentPageIndex)
}

class ScanSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository(application)
    private val recentScanRepository = RecentScanRepository(application)
    private val processScan = ProcessScanUseCase(repository)
    private val exportManager = ExportManager(application)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var autoCaptureLocked = false
    private var activeHistoryId: String? = null
    private var retakeReplaceIndex: Int? = null
    private var pageAdjustmentJob: kotlinx.coroutines.Job? = null

    fun updateLiveDetection(result: DocumentDetectionResult?) {
        _uiState.update { it.copy(liveDetection = result) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onManualCapture(bitmap: Bitmap) {
        handleCapture(bitmap, fromAuto = false)
    }

    fun onAutoCapture(bitmap: Bitmap) {
        if (autoCaptureLocked) return
        autoCaptureLocked = true
        handleCapture(bitmap, fromAuto = true)
        viewModelScope.launch {
            delay(5000)
            autoCaptureLocked = false
        }
    }

    fun resetAutoCaptureLock() {
        autoCaptureLocked = false
    }

    private fun handleCapture(bitmap: Bitmap, fromAuto: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            
            val (captured, isCropped) = withContext(Dispatchers.Default) {
                val detection = _uiState.value.liveDetection
                if (detection != null && detection.corners.size == 4) {
                    val corners = normalizedToPixelCorners(detection.corners, bitmap.width, bitmap.height)
                    val cropped = runCatching { processScan.crop(bitmap, corners) }.getOrNull()
                    if (cropped != null) {
                        cropped to true
                    } else {
                        bitmap to false
                    }
                } else {
                    bitmap to false
                }
            }

            _uiState.update {
                it.copy(
                    pendingCapture = captured,
                    pendingCaptureIsCropped = isCropped,
                    isProcessing = false,
                    showMultiPageBar = true,
                    message = if (fromAuto) getApplication<Application>().getString(
                        com.docscanner.app.R.string.auto_captured
                    ) else null
                )
            }
        }
    }

    private fun normalizedToPixelCorners(
        normalized: List<Point2D>,
        width: Int,
        height: Int
    ): List<Point2D> {
        return normalized.map { Point2D(it.x * width, it.y * height) }
    }

    /** After shutter — detect edges on still image (h4rz scan flow). */
    fun beginCropAdjust(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null, cropAdjust = null) }
            val editable = withContext(Dispatchers.Default) {
                OpenCvBootstrap.init()
                BitmapMatUtils.toEditableBitmap(bitmap)
            }
            val corners = withContext(Dispatchers.Default) {
                detectBestCorners(editable)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    cropAdjust = CropAdjustState(
                        captureBitmap = editable,
                        corners = corners,
                        phase = CaptureFlowPhase.ADJUST_EDGES
                    )
                )
            }
        }
    }

    fun updateCropAdjustCorners(corners: List<Point2D>) {
        val session = _uiState.value.cropAdjust ?: return
        if (corners.size != 4) return
        _uiState.update {
            it.copy(cropAdjust = session.copy(corners = corners))
        }
    }

    fun discardCropAdjust() {
        val session = _uiState.value.cropAdjust
        _uiState.update { it.copy(cropAdjust = null, isProcessing = false) }
        viewModelScope.launch(Dispatchers.Default) {
            recycleCropAdjust(session)
        }
    }

    fun confirmCropAdjust() {
        val session = _uiState.value.cropAdjust ?: return
        if (session.corners.size != 4) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching {
                withContext(Dispatchers.Default) {
                    OpenCvBootstrap.init()
                    val cropped = processScan.crop(session.captureBitmap, session.corners)
                    val scanned = processScan.applyFilter(cropped, ScanFilter.MAGIC_COLOR)
                    Pair(cropped, scanned)
                }
            }.onSuccess { (cropped, scanned) ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        cropAdjust = session.copy(
                            phase = CaptureFlowPhase.SCAN_PREVIEW,
                            croppedBitmap = cropped,
                            scannedPreview = scanned
                        )
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isProcessing = false, message = "Scan failed") }
            }
        }
    }

    fun keepScanningAfterPreview() {
        val session = _uiState.value.cropAdjust ?: return
        val preview = session.scannedPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val page = withContext(Dispatchers.Default) {
                buildPageFromScanSession(session, preview)
            }
            recycleCropAdjust(session)
            _uiState.update {
                val newPages = it.pages + page
                it.copy(
                    pages = newPages,
                    cropAdjust = null,
                    isProcessing = false,
                    currentPageIndex = newPages.lastIndex
                )
            }
            persistToRecentHistory()
        }
    }

    fun exportAfterPreview(onComplete: () -> Unit = {}) {
        val session = _uiState.value.cropAdjust ?: return
        val preview = session.scannedPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val page = withContext(Dispatchers.Default) {
                buildPageFromScanSession(session, preview)
            }
            recycleCropAdjust(session)
            val replaceAt = retakeReplaceIndex
            retakeReplaceIndex = null
            _uiState.update { state ->
                val pages = state.pages.toMutableList()
                val newIndex = if (replaceAt != null && replaceAt in pages.indices) {
                    recyclePage(pages[replaceAt])
                    pages[replaceAt] = page
                    replaceAt
                } else {
                    pages.add(page)
                    pages.lastIndex
                }
                state.copy(
                    pages = pages,
                    cropAdjust = null,
                    isProcessing = false,
                    currentPageIndex = newIndex,
                    previewToken = state.previewToken + 1
                )
            }
            persistToRecentHistory()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun beginRetakeCurrentPage() {
        val index = _uiState.value.currentPageIndex
        if (index !in _uiState.value.pages.indices) return
        retakeReplaceIndex = index
        _uiState.update { it.copy(cropMode = false, cropAdjust = null) }
    }

    fun restoreFromHistory(historyId: String, onReady: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val bitmaps = recentScanRepository.loadPageBitmaps(historyId)
            if (bitmaps.isEmpty()) {
                _uiState.update { it.copy(isProcessing = false, message = "Could not open scan") }
                return@launch
            }
            clearSession()
            activeHistoryId = historyId
            val pages = bitmaps.map { bitmap ->
                ScanPage(
                    originalBitmap = copyBitmap(bitmap),
                    croppedBitmap = copyBitmap(bitmap),
                    displayBitmap = copyBitmap(bitmap),
                    filter = ScanFilter.MAGIC_COLOR
                )
            }
            bitmaps.forEach { it.recycle() }
            _uiState.update {
                it.copy(
                    pages = pages,
                    currentPageIndex = 0,
                    isProcessing = false,
                    previewToken = it.previewToken + 1
                )
            }
            withContext(Dispatchers.Main) { onReady() }
        }
    }

    private fun persistToRecentHistory(pdfFile: File? = null) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            activeHistoryId = recentScanRepository.saveOrUpdateSession(
                existingId = activeHistoryId,
                pages = pages,
                pdfFile = pdfFile
            )
        }
    }

    private suspend fun detectBestCorners(bitmap: Bitmap): List<Point2D> {
        val detected = processScan.detectCornersAggressive(bitmap)
            ?: processScan.detectCorners(bitmap)
        return if (isReliableDocumentQuad(detected, bitmap)) {
            detected!!
        } else {
            defaultCorners(bitmap)
        }
    }

    private suspend fun buildPageFromScanSession(session: CropAdjustState, scannedDisplay: Bitmap): ScanPage {
        val original = copyBitmap(session.captureBitmap)
        val unfiltered = session.croppedBitmap?.let { copyBitmap(it) }
            ?: processScan.crop(original, session.corners)
        return ScanPage(
            originalBitmap = original,
            croppedBitmap = unfiltered,
            displayBitmap = copyBitmap(scannedDisplay),
            corners = session.corners,
            filter = ScanFilter.MAGIC_COLOR
        )
    }

    private fun recycleCropAdjust(session: CropAdjustState?) {
        if (session == null) return
        val seen = mutableSetOf<Bitmap>()
        fun r(b: Bitmap?) {
            if (b == null || b.isRecycled || !seen.add(b)) return
            b.recycle()
        }
        r(session.scannedPreview)
        r(session.croppedBitmap)
        r(session.captureBitmap)
    }

    fun keepScanningAndCaptureNext() {
        val pending = _uiState.value.pendingCapture ?: return
        val alreadyCropped = _uiState.value.pendingCaptureIsCropped
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val page = buildPageFromBitmap(pending, alreadyCropped = alreadyCropped)
            _uiState.update {
                it.copy(
                    pages = it.pages + page,
                    pendingCapture = null,
                    pendingCaptureIsCropped = false,
                    showMultiPageBar = false,
                    isProcessing = false,
                    message = "Page ${it.pages.size + 1} saved"
                )
            }
            resetAutoCaptureLock()
        }
    }

    fun finishCaptureAndOpenEditor(onReady: () -> Unit = {}) {
        val pending = _uiState.value.pendingCapture
        val alreadyCropped = _uiState.value.pendingCaptureIsCropped
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showMultiPageBar = false) }
            val newPages = _uiState.value.pages.toMutableList()
            if (pending != null) {
                newPages.add(buildPageFromBitmap(pending, alreadyCropped = alreadyCropped))
            }
            if (newPages.isEmpty()) {
                _uiState.update { it.copy(isProcessing = false, message = "No pages captured") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    pages = newPages,
                    pendingCapture = null,
                    pendingCaptureIsCropped = false,
                    currentPageIndex = newPages.lastIndex,
                    isProcessing = false
                )
            }
            resetAutoCaptureLock()
            onReady()
        }
    }

    fun addImportedBitmaps(bitmaps: List<Bitmap>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val pages = bitmaps.map { buildPageFromBitmap(it) }
            _uiState.update {
                it.copy(
                    pages = it.pages + pages,
                    currentPageIndex = (it.pages.size + pages.size - 1).coerceAtLeast(0),
                    isProcessing = false,
                    previewToken = it.previewToken + 1
                )
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private suspend fun buildPageFromBitmap(
        bitmap: Bitmap,
        alreadyCropped: Boolean = false
    ): ScanPage {
        val editable = BitmapMatUtils.toEditableBitmap(bitmap)
        if (alreadyCropped) {
            val display = editable.copy(Bitmap.Config.ARGB_8888, true) ?: editable
            return ScanPage(
                originalBitmap = editable,
                croppedBitmap = editable,
                displayBitmap = display,
                corners = defaultCorners(editable),
                filter = ScanFilter.ORIGINAL
            )
        }
        val detected = processScan.detectCorners(editable)
        val corners = if (isReliableDocumentQuad(detected, editable)) {
            detected!!
        } else {
            defaultCorners(editable)
        }
        val cropped = processScan.crop(editable, corners)
        return ScanPage(
            originalBitmap = editable,
            croppedBitmap = cropped,
            displayBitmap = copyBitmap(cropped),
            corners = corners,
            filter = ScanFilter.ORIGINAL
        )
    }

    private fun isReliableDocumentQuad(corners: List<Point2D>?, bitmap: Bitmap): Boolean {
        if (corners == null || corners.size != 4) return false
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val normalized = corners.map { Point2D(it.x / w, it.y / h) }
        var sum = 0f
        for (i in normalized.indices) {
            val j = (i + 1) % 4
            sum += normalized[i].x * normalized[j].y - normalized[j].x * normalized[i].y
        }
        val area = kotlin.math.abs(sum) / 2f
        return area in 0.05f..0.98f
    }

    fun selectPage(index: Int) {
        if (index in _uiState.value.pages.indices) {
            _uiState.update {
                it.copy(
                    currentPageIndex = index,
                    cropMode = false,
                    previewToken = it.previewToken + 1
                )
            }
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val pages = _uiState.value.pages.toMutableList()
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) return
        val moved = pages.removeAt(fromIndex)
        pages.add(toIndex, moved)
        val current = _uiState.value.currentPageIndex
        val newIndex = when {
            current == fromIndex -> toIndex
            fromIndex < current && toIndex >= current -> current - 1
            fromIndex > current && toIndex <= current -> current + 1
            else -> current
        }
        _uiState.update {
            it.copy(
                pages = pages,
                currentPageIndex = newIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                previewToken = it.previewToken + 1
            )
        }
    }

    fun setCropMode(enabled: Boolean) {
        _uiState.update { it.copy(cropMode = enabled) }
    }

    fun updatePageCorners(corners: List<Point2D>) {
        val page = currentPage() ?: return
        page.corners = corners
        _uiState.update { it.copy(pages = it.pages.toList()) }
    }

    fun applyCropToCurrentPage() {
        val page = currentPage() ?: return
        if (page.corners.size != 4) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val cropped = processScan.crop(page.originalBitmap, page.corners)
            page.croppedBitmap?.takeIf { it !== page.originalBitmap }?.recycle()
            page.croppedBitmap = cropped
            page.filter = ScanFilter.ORIGINAL
            page.displayBitmap = copyBitmap(cropped)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    pages = it.pages.toList(),
                    cropMode = false,
                    previewToken = it.previewToken + 1
                )
            }
        }
    }

    private suspend fun refreshDisplayWithFilter(page: ScanPage) {
        val base = page.croppedBitmap ?: page.originalBitmap
        if (base.isRecycled) return

        if (!OpenCvBootstrap.init() && page.filter != ScanFilter.ORIGINAL) {
            throw IllegalStateException("OpenCV not available")
        }

        val newDisplay = if (page.filter == ScanFilter.ORIGINAL) {
            copyBitmap(base)
        } else {
            processScan.applyFilter(base, page.filter)
        }

        withContext(Dispatchers.Main.immediate) {
            val old = page.displayBitmap
            page.displayBitmap = newDisplay
            if (old != null &&
                old !== page.croppedBitmap &&
                old !== page.originalBitmap &&
                old !== newDisplay &&
                !old.isRecycled
            ) {
                old.recycle()
            }
        }
    }

    fun rotateCurrentPage() {
        val page = currentPage() ?: return
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    val matrix = Matrix().apply { postRotate(90f) }
                    val oldW = page.originalBitmap.width
                    val oldH = page.originalBitmap.height

                    // 1. Rotate Original Bitmap
                    val origRotated = Bitmap.createBitmap(
                        page.originalBitmap, 0, 0, oldW, oldH, matrix, true
                    )

                    // 2. Rotate Corners manually
                    val rotatedCorners = page.corners.map { p ->
                        Point2D(oldH.toFloat() - p.y, p.x)
                    }
                    
                    val reorderedCorners = if (rotatedCorners.size == 4) {
                        listOf(
                            rotatedCorners[3], // New TL was Old BL
                            rotatedCorners[0], // New TR was Old TL
                            rotatedCorners[1], // New BR was Old TR
                            rotatedCorners[2]  // New BL was Old BR
                        )
                    } else {
                        defaultCorners(origRotated)
                    }

                    // 3. Generate new cropped bitmap
                    val cropped = processScan.crop(origRotated, reorderedCorners)

                    // 4. Generate new display bitmap with filter and adjustments combined
                    val display = if (page.filter == ScanFilter.ORIGINAL) {
                        copyBitmap(cropped)
                    } else {
                        processScan.applyFilter(cropped, page.filter)
                    }
                    val finalDisplay = com.docscanner.app.scanner.image.ImageAdjustmentProcessor.apply(display, page.adjustments)
                    if (finalDisplay !== display) {
                        display.recycle()
                    }

                    // 5. Update page object properties (safely on background thread)
                    val oldOrig = page.originalBitmap
                    val oldCropped = page.croppedBitmap
                    val oldDisplay = page.displayBitmap

                    page.originalBitmap = origRotated
                    page.croppedBitmap = cropped
                    page.displayBitmap = finalDisplay
                    page.corners = reorderedCorners
                    page.rotationDegrees = (page.rotationDegrees + 90) % 360

                    // 6. Notify UI to refresh and release old bitmaps
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            pages = it.pages.toList(),
                            previewToken = it.previewToken + 1
                        )
                    }

                    // 7. Cleanup old bitmaps AFTER notifying UI (with a small delay to ensure release)
                    delay(100) 
                    if (oldOrig !== origRotated) oldOrig.recycle()
                    if (oldCropped != null && oldCropped !== oldOrig && oldCropped !== cropped) oldCropped.recycle()
                    if (oldDisplay != null && oldDisplay !== finalDisplay && !oldDisplay.isRecycled) {
                         // Extra check to ensure it's not being used as original/cropped elsewhere
                         if (oldDisplay !== origRotated && oldDisplay !== cropped) {
                             oldDisplay.recycle()
                         }
                    }
                }
            }.onFailure { e ->
                Log.e("ScanViewModel", "Rotate failed", e)
                _uiState.update { it.copy(isProcessing = false, message = "Rotate failed") }
            }
        }
    }

    private fun canRecycleDisplay(bitmap: Bitmap, page: ScanPage): Boolean {
        if (bitmap.isRecycled) return false
        return bitmap !== page.originalBitmap && bitmap !== page.croppedBitmap
    }

    fun applyFilter(filter: ScanFilter) {
        val page = currentPage() ?: return
        val base = page.croppedBitmap ?: page.originalBitmap
        if (base.isRecycled) return

        page.filter = filter

        if (filter == ScanFilter.ORIGINAL) {
            val copy = copyBitmap(base)
            val old = page.displayBitmap
            page.displayBitmap = copy
            if (old != null &&
                old !== page.croppedBitmap &&
                old !== page.originalBitmap &&
                old !== copy &&
                !old.isRecycled
            ) {
                old.recycle()
            }
            _uiState.update {
                it.copy(
                    pages = it.pages.toList(),
                    message = null,
                    previewToken = it.previewToken + 1
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    refreshDisplayWithFilter(page)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        pages = it.pages.toList(),
                        previewToken = it.previewToken + 1
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(isProcessing = false, message = "Filter failed")
                }
            }
        }
    }

    private fun copyBitmap(source: Bitmap): Bitmap {
        val copy = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(copy).drawBitmap(source, 0f, 0f, null)
        return copy
    }

    fun deleteCurrentPage() {
        val index = _uiState.value.currentPageIndex
        val pages = _uiState.value.pages.toMutableList()
        if (index !in pages.indices) return
        recyclePage(pages.removeAt(index))
        val newIndex = (index - 1).coerceAtLeast(0).coerceAtMost((pages.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(pages = pages, currentPageIndex = if (pages.isEmpty()) 0 else newIndex) }
    }

    fun exportPdf(saveToDownloads: Boolean, onResult: (File?) -> Unit) {
        val bitmaps = _uiState.value.pages.map { it.workingBitmap() }
        if (bitmaps.isEmpty()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val file = runCatching {
                if (saveToDownloads) {
                    exportManager.savePdfToDownloads(bitmaps)
                } else {
                    repository.savePdf(bitmaps, "SCAN")
                }
            }.getOrNull()
            _uiState.update {
                it.copy(isProcessing = false, lastExportFile = file, message = file?.name)
            }
            if (file != null) persistToRecentHistory(pdfFile = file)
            withContext(Dispatchers.Main) { onResult(file) }
        }
    }

    fun exportJpegs(saveToDownloads: Boolean, onResult: (List<File>) -> Unit) {
        val bitmaps = _uiState.value.pages.map { it.workingBitmap() }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val files = runCatching {
                if (saveToDownloads) {
                    exportManager.saveJpegsToDownloads(bitmaps)
                } else {
                    bitmaps.map { repository.saveJpeg(it, "SCAN") }
                }
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(isProcessing = false) }
            withContext(Dispatchers.Main) { onResult(files) }
        }
    }

    fun getShareUri(file: File) = repository.getShareUri(file)

    fun formatSize(bytes: Long) = exportManager.formatSize(bytes)

    fun compressAndDownloadPdf(qualityPercent: Int, onResult: (CompressionResult?) -> Unit) {
        val bitmaps = _uiState.value.pages.map { it.workingBitmap() }
        if (bitmaps.isEmpty()) {
            viewModelScope.launch { withContext(Dispatchers.Main) { onResult(null) } }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            val result = runCatching {
                exportManager.compressAndSavePdfToDownloads(bitmaps, qualityPercent)
            }.getOrNull()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    lastExportFile = result?.file,
                    lastCompression = result,
                    message = result?.let { r ->
                        "Compressed ${exportManager.formatSize(r.compressedSizeBytes)}"
                    }
                )
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun compressAndDownloadJpegs(qualityPercent: Int, onResult: (List<CompressionResult>) -> Unit) {
        val bitmaps = _uiState.value.pages.map { it.workingBitmap() }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val results = runCatching {
                exportManager.compressAndSaveJpegsToDownloads(bitmaps, qualityPercent)
            }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    lastCompression = results.firstOrNull(),
                    message = "Saved ${results.size} images"
                )
            }
            withContext(Dispatchers.Main) { onResult(results) }
        }
    }

    fun updatePageAdjustment(adjustment: com.docscanner.app.domain.model.ImageAdjustment, value: Int) {
        val page = currentPage() ?: return
        page.adjustments[adjustment] = value.coerceIn(-100, 100)
        pageAdjustmentJob?.cancel()
        pageAdjustmentJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val display = withContext(Dispatchers.Default) {
                    rebuildPageDisplay(page)
                }
                withContext(Dispatchers.Main.immediate) {
                    page.displayBitmap?.takeIf { canRecycleDisplay(it, page) }?.recycle()
                    page.displayBitmap = display
                }
                _uiState.update {
                    it.copy(previewToken = it.previewToken + 1)
                }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private suspend fun rebuildPageDisplay(page: ScanPage): Bitmap {
        val base = page.croppedBitmap ?: page.originalBitmap
        val filtered = if (page.filter == ScanFilter.ORIGINAL) {
            copyBitmap(base)
        } else {
            processScan.applyFilter(base, page.filter)
        }
        return com.docscanner.app.scanner.image.ImageAdjustmentProcessor.apply(filtered, page.adjustments)
    }

    private fun applyPageAdjustments(page: ScanPage): Bitmap {
        val base = page.displayBitmap ?: page.croppedBitmap ?: page.originalBitmap
        return com.docscanner.app.scanner.image.ImageAdjustmentProcessor.apply(base, page.adjustments)
    }

    fun clearSession() {
        _uiState.value.pages.forEach { recyclePage(it) }
        recycleCropAdjust(_uiState.value.cropAdjust)
        _uiState.value.pendingCapture?.recycle()
        _uiState.value = ScanUiState()
        autoCaptureLocked = false
        activeHistoryId = null
        retakeReplaceIndex = null
    }

    private fun recyclePage(page: ScanPage) {
        val seen = mutableSetOf<Bitmap>()
        fun safeRecycle(b: Bitmap?) {
            if (b == null || b.isRecycled || !seen.add(b)) return
            b.recycle()
        }
        safeRecycle(page.displayBitmap)
        safeRecycle(page.croppedBitmap)
        safeRecycle(page.originalBitmap)
    }

    private fun defaultCorners(bitmap: Bitmap): List<Point2D> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val m = 0.02f
        return listOf(
            Point2D(w * m, h * m),
            Point2D(w * (1 - m), h * m),
            Point2D(w * (1 - m), h * (1 - m)),
            Point2D(w * m, h * (1 - m))
        )
    }

    fun currentPage(): ScanPage? = _uiState.value.pages.getOrNull(_uiState.value.currentPageIndex)

    override fun onCleared() {
        clearSession()
        super.onCleared()
    }
}
