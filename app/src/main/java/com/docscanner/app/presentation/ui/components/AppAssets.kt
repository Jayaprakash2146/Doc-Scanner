package com.docscanner.app.presentation.ui.components

import androidx.annotation.DrawableRes
import com.docscanner.app.R
import com.docscanner.app.domain.model.ScanFilter

object AppAssets {
    @DrawableRes val logo = R.drawable.docscanner_logo_512

    @DrawableRes val capture = R.drawable.btn_capture
    @DrawableRes val flash = R.drawable.btn_flash
    @DrawableRes val gallery = R.drawable.btn_gallery
    @DrawableRes val settings = R.drawable.btn_settings

    @DrawableRes val autoDetect = R.drawable.btn_auto_detect
    @DrawableRes val crop = R.drawable.btn_crop
    @DrawableRes val delete = R.drawable.btn_delete
    @DrawableRes val rotate = R.drawable.btn_rotate

    @DrawableRes val save = R.drawable.btn_save
    @DrawableRes val exportPdf = R.drawable.btn_export_pdf
    @DrawableRes val share = R.drawable.btn_share
    @DrawableRes val ocr = R.drawable.btn_ocr_text

    fun filterIcon(filter: ScanFilter): Int = when (filter) {
        ScanFilter.ORIGINAL -> R.drawable.btn_enhance
        ScanFilter.GRAYSCALE -> R.drawable.btn_grayscale
        ScanFilter.BLACK_WHITE -> R.drawable.btn_bw_filter
        ScanFilter.MAGIC_COLOR -> R.drawable.btn_color_filter
        ScanFilter.SHARPEN -> R.drawable.btn_enhance
    }
}
