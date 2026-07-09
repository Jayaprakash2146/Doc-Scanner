package com.docscanner.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.docscanner.app.scanner.opencv.BitmapMatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BitmapLoader {

    suspend fun loadUris(context: Context, uris: List<Uri>): List<Bitmap> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { BitmapMatUtils.downscale(it, 2400) }
            }
        }
    }
}
