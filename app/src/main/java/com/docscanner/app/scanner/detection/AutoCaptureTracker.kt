package com.docscanner.app.scanner.detection

/**
 * Same idea as Compose CameraScreen: hold document in frame for [holdDurationMs], then capture.
 */
class AutoCaptureTracker(
    private val holdDurationMs: Long = 2000L
) {
    private var holdStartMs: Long = 0L

    fun onDocumentVisible(visible: Boolean): Float {
        if (!visible) {
            holdStartMs = 0L
            return 0f
        }
        val now = System.currentTimeMillis()
        if (holdStartMs == 0L) holdStartMs = now
        return ((now - holdStartMs).toFloat() / holdDurationMs).coerceIn(0f, 1f)
    }

    fun isReady(): Boolean = onDocumentVisible(true) >= 1f && holdStartMs > 0L &&
        System.currentTimeMillis() - holdStartMs >= holdDurationMs

    fun reset() {
        holdStartMs = 0L
    }
}
