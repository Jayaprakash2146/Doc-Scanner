package com.docscanner.app.domain.model

enum class ImageAdjustment(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SHARPEN("Sharpen"),
    SATURATION("Saturation"),
    WARMTH("Warmth"),
    EXPOSURE("Exposure"),
    HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows");

    companion object {
        fun defaults(): Map<ImageAdjustment, Int> = entries.associateWith { 0 }
    }
}
