package com.docscanner.app.scanner.detection

import com.docscanner.app.domain.model.Point2D

/** Normalized corner coordinates (0..1) relative to analyzed frame. */
data class DocumentDetectionResult(
    val corners: List<Point2D>,
    val isStable: Boolean,
    val coverage: Float,
    val frameWidth: Int = 1,
    val frameHeight: Int = 1,
    val stabilityProgress: Float = 0f
)
