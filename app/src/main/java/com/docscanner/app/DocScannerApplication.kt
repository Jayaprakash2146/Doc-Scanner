package com.docscanner.app

import android.app.Application
import android.util.Log
import com.docscanner.app.scanner.opencv.OpenCvBootstrap

class DocScannerApplication : Application() {

    val isOpenCvReady: Boolean
        get() = OpenCvBootstrap.isReady

    override fun onCreate() {
        super.onCreate()
        val ready = OpenCvBootstrap.init()
        if (!ready) {
            Log.e(TAG, "OpenCV initialization failed; document detection will be unavailable")
        }
    }

    companion object {
        private const val TAG = "DocScannerApp"
    }
}
