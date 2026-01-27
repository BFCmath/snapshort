# Snapshort: User Interface & Flow

This document outlines the UI structure, Jetpack Compose implementation, and navigation flow of the Snapshort app.

## UI Overview

Snapshort uses **Jetpack Compose** for its entire UI. The design follows Material Design 3 guidelines with a focus on simplicity and responsiveness.

## Main Screen Flow

The [MainActivity](file:///d:/project/android/snapshort/app/src/main/java/com/example/snapshort/MainActivity.kt) serves as the host for the application's single screen.

### 1. Permission State (Onboarding)
If the Accessibility Service is not enabled:
- The app displays `AccessibilityPermissionScreen`.
- A clear call-to-action (CTA) directs the user to the system Accessibility Settings.
- The UI uses `LaunchedEffect` with `Lifecycle.State.RESUMED` to auto-check the permission status when the user returns from settings.

### 2. Gallery State
Once enabled, the app displays the `GalleryScreen`:
- **Refresh**: Uses `PullToRefreshBox` for manual list updates.
- **Data Binding**: The list of screenshots is kept in a `mutableStateOf` and updated via the `ScreenshotRepository`.

## Components

### [GalleryScreen](file:///d:/project/android/snapshort/app/src/main/java/com/example/snapshort/ui/GalleryScreen.kt)
The primary UI component for browsing screenshots.

- **Grid Layout**: Uses `LazyVerticalGrid` with a fixed column count (3) and `4.dp` spacing.
- **Image Preview Transition**: Uses `AnimatedVisibility` with `fadeIn` and `fadeOut` for smooth entry/exit of the full-screen mode.
- **Performance**:
    - Uses **Coil** for asynchronous loading with `crossfade(true)`.
    - Items are keyed by `absolutePath` in the grid to maintain scroll position during list updates.

### Full-Screen Viewer Logic
Implemented within `GalleryScreen`, the viewer uses a high-performance gesture engine:
- **Gesture Detection**: Uses `Modifier.pointerInput` with `detectTransformGestures`.
- **Transformation Layer**: Applies `scaleX`, `scaleY`, `translationX`, and `translationY` via `graphicsLayer` to avoid recomposition stutters.
- **Zoom Range**: Constrained between **0.5x and 5.0x** using `coerceIn`.
- **Automatic Reset**: When the scale is 1.0x or less, offsets are automatically reset to `0f` to keep the image centered.

## Interaction with Services

The UI interaction is reactive:
- **Permission Polling**: `MainActivity` polls `isAccessibilityServiceEnabled` on every `onResume` event to ensure the UI instantly reflects service activation.
- **Manual Refresh**: `PullToRefreshBox` allows users to force a repository reload if the `FileObserver` missed an event (e.g., during low-power modes).
