package com.docscanner.app.presentation.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscanner.app.data.repository.ScanRepository
import com.docscanner.app.domain.model.Point2D
import com.docscanner.app.domain.model.ScanFilter
import com.docscanner.app.domain.usecases.ProcessScanUseCase
import com.docscanner.app.presentation.ScanSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ScannerViewModel"

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository(application)
    private val processScan = ProcessScanUseCase(repository)

    private val _state = MutableStateFlow(ScanSessionState())
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    fun onPhotoCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true, message = null) }
                val corners = processScan.detectCorners(bitmap)
                val finalCorners = corners ?: defaultFullFrameCorners(bitmap)
                
                _state.update {
                    it.copy(
                        currentCapturedBitmap = bitmap,
                        corners = finalCorners,
                        showMultiPageOption = true,
                        message = if (corners == null) "Edges not found." else "Detected!"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
                _state.update { it.copy(message = "Error: ${e.localizedMessage}") }
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun onGalleryImported(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true, message = null) }
                val corners = processScan.detectCorners(bitmap)
                val finalCorners = corners ?: defaultFullFrameCorners(bitmap)
                val cropped = processScan.crop(bitmap, finalCorners)
                
                _state.update {
                    it.copy(
                        capturedBitmaps = it.capturedBitmaps + bitmap,
                        currentCapturedBitmap = bitmap,
                        croppedBitmap = cropped,
                        displayBitmap = cropped,
                        corners = finalCorners,
                        showMultiPageOption = false,
                        selectedFilter = ScanFilter.ORIGINAL
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun continueScanning() {
        val current = _state.value.currentCapturedBitmap ?: return
        _state.update {
            it.copy(
                capturedBitmaps = it.capturedBitmaps + current,
                currentCapturedBitmap = null,
                showMultiPageOption = false,
                message = "Page added"
            )
        }
    }

    fun stopAndProcess() {
        val current = _state.value.currentCapturedBitmap
        val bitmaps = if (current != null) _state.value.capturedBitmaps + current else _state.value.capturedBitmaps
        
        if (bitmaps.isEmpty()) return

        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true, showMultiPageOption = false) }
                val lastBitmap = bitmaps.last()
                val corners = _state.value.corners.takeIf { it.size == 4 } ?: processScan.detectCorners(lastBitmap) ?: defaultFullFrameCorners(lastBitmap)
                val cropped = processScan.crop(lastBitmap, corners)
                
                _state.update {
                    it.copy(
                        capturedBitmaps = bitmaps,
                        currentCapturedBitmap = lastBitmap,
                        croppedBitmap = cropped,
                        displayBitmap = cropped,
                        corners = corners,
                        selectedFilter = ScanFilter.ORIGINAL
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                _state.update { it.copy(message = "Failed to process image") }
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun startAdjustingEdges() {
        _state.update { it.copy(isAdjustingEdges = true, tempCorners = it.corners) }
    }

    fun updateTempCorners(corners: List<Point2D>) {
        _state.update { it.copy(tempCorners = corners) }
    }

    fun applyAdjustedEdges() {
        val captured = _state.value.currentCapturedBitmap ?: return
        val newCorners = _state.value.tempCorners
        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true) }
                val cropped = processScan.crop(captured, newCorners)
                _state.update {
                    it.copy(
                        croppedBitmap = cropped,
                        displayBitmap = cropped, // Reset filter when re-cropping as requested? Or keep filter? 
                        // Usually re-cropping resets. Let's reset for simplicity.
                        corners = newCorners,
                        isAdjustingEdges = false,
                        selectedFilter = ScanFilter.ORIGINAL
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Adjust edges failed", e)
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun discardAdjustedEdges() {
        _state.update { it.copy(isAdjustingEdges = false, tempCorners = emptyList()) }
    }

    fun rotateCaptured() {
        val captured = _state.value.currentCapturedBitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(captured, 0, 0, captured.width, captured.height, matrix, true)
        
        _state.update { it.copy(currentCapturedBitmap = rotated, isProcessing = true) }
        
        viewModelScope.launch {
            try {
                val corners = processScan.detectCorners(rotated) ?: defaultFullFrameCorners(rotated)
                val cropped = processScan.crop(rotated, corners)
                
                _state.update {
                    it.copy(
                        croppedBitmap = cropped,
                        displayBitmap = cropped,
                        corners = corners,
                        selectedFilter = ScanFilter.ORIGINAL
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rotate failed", e)
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun selectFilter(filter: ScanFilter) {
        if (_state.value.selectedFilter == filter) return
        
        val base = _state.value.croppedBitmap ?: return
        
        if (filter == ScanFilter.ORIGINAL) {
            _state.update { it.copy(displayBitmap = base, selectedFilter = filter) }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true) }
                val result = processScan.applyFilter(base, filter)
                _state.update {
                    it.copy(displayBitmap = result, selectedFilter = filter)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Filter failed", e)
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun saveJpeg() {
        val bitmap = _state.value.displayBitmap ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true) }
                val file = repository.saveJpeg(bitmap)
                _state.update {
                    it.copy(
                        lastSavedPath = file.absolutePath,
                        message = "Saved JPEG"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun savePdf() {
        val bitmap = _state.value.displayBitmap ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true) }
                val file = repository.savePdf(listOf(bitmap))
                _state.update {
                    it.copy(
                        lastSavedPath = file.absolutePath,
                        message = "Saved PDF"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "PDF failed", e)
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun getShareUri() = _state.value.lastSavedPath?.let { path ->
        repository.getShareUri(java.io.File(path))
    }

    fun reset() {
        _state.update { ScanSessionState() }
    }

    private fun defaultFullFrameCorners(bitmap: Bitmap): List<Point2D> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val m = 0.05f
        return listOf(
            Point2D(w * m, h * m),
            Point2D(w * (1 - m), h * m),
            Point2D(w * (1 - m), h * (1 - m)),
            Point2D(w * m, h * (1 - m))
        )
    }
}
