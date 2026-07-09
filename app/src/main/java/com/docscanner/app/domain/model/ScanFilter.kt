package com.docscanner.app.domain.model

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("Black & White"),
    MAGIC_COLOR("Magic Color"),
    SHARPEN("Sharpen")
}
