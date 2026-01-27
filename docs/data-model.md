# Snapshort: Data & Storage

This document describes how Snapshort manages screenshot data, storage locations, and the repository pattern used in the app.

## Storage Strategy

Snapshort manages screenshots in two distinct locations:
1.  **System Public Directory**: The initial location where Android saves the screenshot after `performGlobalAction` is called.
2.  **App Internal Storage**: A private directory where Snapshort copies the images for management and display.

### Internal Storage Path
The app saves screenshots to:
`/data/user/0/com.example.snapshort/files/screenshots/`

This directory is private to the application and is not visible to other apps or the system gallery, providing a dedicated space for Snapshort captures.

## [ScreenshotRepository](file:///d:/project/android/snapshort/app/src/main/java/com/example/snapshort/data/ScreenshotRepository.kt)

The `ScreenshotRepository` is the single source of truth for screenshot data.

### Key Capabilities

| Feature | Method | Description |
| :--- | :--- | :--- |
| **Fetch** | `getScreenshots()` | Returns a descending list of files (newest first) from internal storage. |
| **Sync** | `copyScreenshotToInternal(File)` | Copies a file from an external source (like the system screenshot folder) to internal storage. |
| **Direct Save** | `saveScreenshot(Bitmap)` | Compresses and saves a `Bitmap` object directly to a PNG file in internal storage. |
| **Load** | `loadBitmap(File)` | Decodes a file into a `Bitmap` using `Dispatchers.IO`. |
| **Cleanup** | `deleteScreenshot(File)` | Removes a specific file. |

## File Observer Mechanism

In `ScreenshotAccessibilityService`, a `FileObserver` is used to bridge the gap between the system's capture and the app's internal storage:

- **Path Discovery**: It attempts to watch `/Pictures/Screenshots` first, falling back to `/DCIM/Screenshots` if the primary doesn't exist.
- **Event Mask**: Uses `CREATE or CLOSE_WRITE`. `CLOSE_WRITE` is essential for ensuring the system has finished writing the large image file before the app attempts to copy it.
- **Race Condition Handling**: A `1000ms` delay is implemented via `handler.postDelayed` after detection. This is a critical heuristic to ensure the file is fully "unlocked" by the system's screenshot process.

## Data Reliability

- **Transactional Sync**: The `copyScreenshotToInternal` method uses a timestamped filename (`screenshot_${System.currentTimeMillis()}.png`) to avoid collisions.
- **Background I/O**: `ScreenshotRepository.loadBitmap` uses `Dispatchers.IO` to decode files, ensuring the main thread remains responsive even when loading high-resolution images.
- **Cleanup API**: Provides both `deleteScreenshot(File)` for individual removal and `deleteAllScreenshots()` for cache clearing.
