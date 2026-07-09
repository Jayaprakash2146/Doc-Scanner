package com.docscanner.app.scanner.opencv

import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCvBootstrap {
    private const val TAG = "OpenCvBootstrap"

    @Volatile
    var isReady: Boolean = false
        private set

    fun init(): Boolean {
        if (isReady) return true
        isReady = runCatching {
            OpenCVLoader.initLocal() || OpenCVLoader.initDebug()
        }.getOrDefault(false)
        if (!isReady) {
            Log.e(TAG, "OpenCV failed to load — document detection disabled")
        } else {
            Log.i(TAG, "OpenCV loaded")
        }
        return isReady
    }
}
