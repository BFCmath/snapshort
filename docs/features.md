# Snapshort: Feature Implementation Details

This document provides a technical breakdown of each primary feature in the Snapshort application, detailing the APIs used and the logic behind the implementation.

---

## 1. Silent Screenshot Capture
Capturing screenshots without triggering the system's "Screen Recording" or "Media Projection" confirmation dialogs.

- **Implementation Stack**: 
    - `AccessibilityService`
    - `GLOBAL_ACTION_TAKE_SCREENSHOT` (Android 9/API 28+)
- **How it works**:
    - Instead of using the `MediaProjection` API (which is standard but invasive), Snapshort uses the **Accessibility Global Action** for screenshots.
    - When `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)` is called, the system performs the hardware-key-equivalent capture logic.
    - This allows for "one-tap" capture without user confirmation, provided the user has explicitly enabled the Accessibility Service.

---

## 2. Quick Settings Integration
Providing a globally accessible trigger from the Android state bar.

- **Implementation Stack**:
    - `TileService`
    - `StatusBarManager` (via Reflection)
- **How it works**:
    - **Tile Lifecycle**: The `qsTile` state is updated within `onStartListening()` based on the Accessibility Service's connectivity.
    - **Notification Shade Collapse**: To prevent the "Quick Settings" panel from appearing in the screenshot, the app must close it before capture. 
    - **Collapse Logic**: The app attempts to use `collapseShade()` for Android 14+ or sends `Intent.ACTION_CLOSE_SYSTEM_DIALOGS` for older versions. It also includes a fallback that reflectively calls `collapsePanels` on `StatusBarManager` to ensure compatibility across OEM skins.
    - **Trigger Backup**: Clicking the tile invokes the `AccessibilityService` directly via a static singleton instance, with a broadcast receiver acting as a secondary signal path.

---

## 3. Background Image Synchronization
Automatically importing system-captured screenshots into the app's private gallery.

- **Implementation Stack**:
    - `FileObserver`
    - `Handler` (Main Looper)
    - `ScreenshotRepository`
- **How it works**:
    - **Heuristic Path Mapping**: The app scans for `/Pictures/Screenshots` and `/DCIM/Screenshots` to cover most Android device configurations.
    - **Event Masking**: Listens for `CREATE` (file created) and `CLOSE_WRITE` (file finished writing). 
    - **Stabilization Delay**: A `1000ms` delay is used before processing. This is because `FileObserver` events can fire before the system has finished flushing the image data to disk, which would lead to "Corrupted Image" errors if copied immediately.
    - **Internal Scoping**: Images are copied to `context.filesDir`, ensuring they are managed by the app even if the user manually cleans up their public gallery.

---

## 4. Jetpack Compose Screenshot Gallery
A high-performance grid for browsing and managing captures.

- **Implementation Stack**:
    - `LazyVerticalGrid`
    - `Coil Compose`
    - `ScreenshotRepository`
- **How it works**:
    - **State Hoisting**: `MainActivity` holds the state of the screenshot list, which is refreshed automatically on `RESUMED` using `Lifecycle.repeatOnLifecycle`.
    - **Image Optimization**: `Coil` handles the memory-intensive task of loading and caching high-resolution PNGs. It uses a `crossfade` animation for a premium feel.
    - **Efficient Diffing**: Items in the `LazyVerticalGrid` are keyed by their absolute file path, allowing Compose to perform efficient partial UI updates when images are added or deleted.

---

## 5. Pro-Level Image Viewer
Full-screen viewing with interactive inspection tools.

- **Implementation Stack**:
    - `Modifier.pointerInput`
    - `detectTransformGestures`
    - `graphicsLayer`
- **How it works**:
    - **Interaction**: Uses multi-touch gesture detection to update `scale` and `offset` state variables.
    - **Zoom Ceiling**: Constrained to `5.0x` zoom to prevent excessive pixelation.
    - **Performance (GPU)**: Instead of re-layouting the image on every move, it uses `graphicsLayer`. This moves the transformation to the GPU (RenderThread), ensuring 60+ FPS smoothness during zoom/pan.
    - **Logic Reset**: Implements logic to reset pan offsets to `0f` whenever the user zooms back out to the original size, preventing the image from getting "lost" off-screen.

---

## 6. Smart Onboarding & Permissions
Guiding users through the complex Accessibility setup.

- **Implementation Stack**:
    - `AccessibilityManager`
    - `Settings.ACTION_ACCESSIBILITY_SETTINGS`
- **How it works**:
    - **Passive Detection**: The app polls for its own package in the list of enabled accessibility services.
    - **Conditional UI**: The entire `MainActivity` content is swapped for an onboarding card if permission is missing, preventing user confusion.
    - **Deep Linking**: The "Open Settings" button uses a direct Intent to the system's Accessibility list to reduce friction.
