# Snapshort: Feature Implementation Details

This document provides a technical breakdown of each primary feature in the Snapshort application.

---

## 1. Silent Screenshot Capture

Capturing screenshots without triggering the system's "Screen Recording" or "Media Projection" confirmation dialogs.

### Android 11+ (API 30+) - Direct Bitmap Capture
- **API**: `AccessibilityService.takeScreenshot(int displayId, Executor executor, TakeScreenshotCallback callback)`
- **Capability**: Requires `android:canTakeScreenshot="true"` in accessibility service config
- **Returns**: `ScreenshotResult` containing `HardwareBuffer` and `ColorSpace`
- **Storage**: Bitmap saved directly to app's internal storage - never touches public folders

### Android 9-10 (API 28-29) - Fallback
- **API**: `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`
- **Behavior**: System saves screenshot to `/Pictures/Screenshots`
- **Limitation**: Screenshot appears in user's Photos app

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
} else {
    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
}
```

---

## 2. Quick Settings Integration

Providing a globally accessible trigger from the Android status bar.

- **Implementation**: `TileService` + `AccessibilityService`
- **State Management**: Tile shows `STATE_ACTIVE` when accessibility service is enabled
- **Trigger**: `TileService` → `AccessibilityService.takeScreenshot()`

### Panel Dismiss Before Capture

| Android Version | Method | API |
|-----------------|--------|-----|
| **12+ (API 31)** | `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` | Official Accessibility API |
| **11 (API 30)** | Timing-based (450ms delay) | N/A |

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
}
handler.postDelayed({ performScreenshot() }, 400L)
```

This ensures the captured screenshot shows the underlying app, not the Quick Settings panel.

---

## 3. Instant Preview Overlay

Immediately after capture, a floating preview appears.

- **Design**: Edge-to-Edge transparent activity (no window insets/margins).
- **Interaction**:
    - **Auto-Dismiss**: Slides out after 2.5 seconds.
    - **Swipe**: Manual dismiss gesture (drag left).
    - **Edit**: Tapping opens the Editor.
- **Implementation**: Jetpack Compose `Box` with `Animatable` offsets.

---

## 4. Private Screenshot Storage

All screenshots on Android 11+ are saved directly to the app's private internal storage.

| Aspect | Value |
|--------|-------|
| **Path** | `/data/user/0/com.example.snapshort/files/screenshots/` |
| **Format** | PNG (ARGB_8888) |
| **Naming** | `screenshot_{timestamp}.png` |
| **Visibility** | Not visible to other apps or system gallery |

---

## 4. Jetpack Compose Screenshot Gallery

A high-performance grid for browsing and managing captures.

- **Layout**: `LazyVerticalGrid` with 3 fixed columns
- **Image Loading**: Coil with crossfade animation
- **State Management**: `mutableStateOf<List<File>>` refreshed on `RESUMED`
- **Item Keys**: File absolute paths for efficient diffing

---

## 5. Full-Screen Image Viewer

Interactive inspection with gesture controls.

| Gesture | Implementation | Range |
|---------|----------------|-------|
| Pinch to Zoom | `detectTransformGestures` | 0.5x to 5.0x |
| Pan/Drag | `translationX/Y` | Only when zoomed >1x |
| Reset | Auto when zoom ≤1x | Centers image |

- **Performance**: Uses `graphicsLayer` for GPU-accelerated transformations
- **Actions**: Close button, Delete button with confirmation

---

## 6. Smart Onboarding & Permissions

Guiding users through Accessibility Service setup.

- **Detection**: Polls `AccessibilityManager.getEnabledAccessibilityServiceList()`
- **UI State**: Conditional render - onboarding card vs gallery
- **Deep Link**: Direct intent to `Settings.ACTION_ACCESSIBILITY_SETTINGS`
- **Auto-refresh**: Checks permission on every `onResume` via lifecycle observer
