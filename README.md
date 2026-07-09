# Doc Scanner — Adobe Scan–style Android app

Professional document scanner built with **Kotlin**, **XML + ViewBinding**, **CameraX**, **OpenCV**, and **Material 3**.

## Features

- Splash screen (Android 12 SplashScreen API, 2s fade)
- Live camera preview with **real-time document polygon overlay**
- **Auto-capture** when document is stable ~1 second
- Manual capture, flash toggle (OFF / ON / AUTO cycle)
- Gallery import (multiple images)
- Perspective correction & draggable crop handles
- Filters: Original, Magic Color, B&W, Grayscale
- Rotate, retake, delete page
- **Multi-page** scanning with thumbnail strip
- PDF export (multi-page, A4 scaling), save to **Downloads/DocumentsScanner/**
- Share PDF / images

## Open in Android Studio

1. **File → Open** → `Scanner` folder  
2. **Gradle Sync**  
3. Run on a **physical device** (recommended for camera + OpenCV)

## Project structure

```
app/src/main/java/com/docscanner/app/
├── ui/           splash, main, camera, editor, export, adapter
├── viewmodel/    ScanSessionViewModel
├── camera/       CameraController, CameraImageUtils
├── scanner/      OpenCV detection, filters, PDF
├── export/       Downloads export via MediaStore
├── data/         ScanRepository
└── domain/       models, use cases
```

## Tech stack

| Component | Library |
|-----------|---------|
| UI | XML, ViewBinding, Material 3 |
| Camera | CameraX (Preview, ImageCapture, ImageAnalysis) |
| CV | OpenCV 4.9.0 |
| PDF | PdfBox Android |
| Images | Coil |
| Async | Coroutines |

## Permissions

- `CAMERA`
- `READ_MEDIA_IMAGES` (Android 13+)

## Design assets

Source PNGs: `pngsss/`  
App resources: `app/src/main/res/drawable/` and `mipmap-*/`
