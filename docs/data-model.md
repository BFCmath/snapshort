# Snapshort: Data & Storage

This document describes how Snapshort manages screenshot data and storage.

## Storage Strategy

Snapshort saves screenshots **directly to internal storage** on Android 11+. No external storage permissions are required.

| Android Version | Storage Location | Permissions Needed |
|-----------------|------------------|-------------------|
| **11+ (API 30+)** | App internal storage only | None |
| **9-10 (API 28-29)** | System public folder (fallback) | READ_EXTERNAL_STORAGE |

### Internal Storage Path
```
/data/user/0/com.example.snapshort/files/screenshots/
```

This directory is private to the application and not visible to other apps or the system gallery.

## [ScreenshotRepository](file:///d:/project/android/snapshort/app/src/main/java/com/example/snapshort/data/ScreenshotRepository.kt)

The `ScreenshotRepository` is the single source of truth for screenshot data.

### Key Capabilities

| Feature | Method | Description |
| :--- | :--- | :--- |
| **Fetch** | `getScreenshots()` | Returns a descending list of files (newest first) |
| **Direct Save** | `saveScreenshot(Bitmap)` | Compresses bitmap to PNG and saves to internal storage |
| **Load** | `loadBitmap(File)` | Decodes file to `Bitmap` using `Dispatchers.IO` |
| **Delete** | `deleteScreenshot(File)` | Removes a specific file |
| **Clear** | `deleteAllScreenshots()` | Removes all screenshots |

## Capture Flow (Android 11+)

```mermaid
flowchart LR
    A[takeScreenshot API] --> B[HardwareBuffer]
    B --> C[Bitmap.wrapHardwareBuffer]
    C --> D[Copy to ARGB_8888]
    D --> E[repository.saveScreenshot]
    E --> F[PNG in internal storage]
```

1. `AccessibilityService.takeScreenshot()` returns `ScreenshotResult`
2. `HardwareBuffer` is wrapped to create a hardware `Bitmap`
3. Converted to software bitmap (`ARGB_8888`) for file I/O
4. Compressed as PNG and saved via `FileOutputStream`

## Data Reliability

- **Timestamped Naming**: `screenshot_${System.currentTimeMillis()}.png` avoids collisions
- **Background I/O**: `loadBitmap` uses `Dispatchers.IO` for responsive UI
- **Memory Management**: Hardware buffers and bitmaps are recycled after use
