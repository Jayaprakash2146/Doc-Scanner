# OpenCV
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** { *; }

# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
