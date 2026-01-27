# Snapshort: Integration & Setup

This document provides technical requirements and configuration details for Snapshort's core services.

## Android Manifest Requirements

The [AndroidManifest.xml](file:///d:/project/android/snapshort/app/src/main/AndroidManifest.xml) declares permissions and services.

### Permissions

| Permission | Protection Level | Purpose |
| :--- | :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Signature | Required to host an Accessibility Service |
| `BIND_QUICK_SETTINGS_TILE` | Signature | Required to host a Quick Settings Tile |

> [!NOTE]  
> No storage permissions are required on Android 11+ since screenshots are saved directly to internal storage via the `takeScreenshot()` API.

### Service Declarations

#### 1. Accessibility Service
```xml
<service
    android:name=".service.ScreenshotAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true"
    android:label="@string/accessibility_service_label">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

#### 2. Tile Service
```xml
<service
    android:name=".service.ScreenshotTileService"
    android:label="@string/tile_label"
    android:icon="@drawable/ic_screenshot"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
    <meta-data
        android:name="android.service.quicksettings.ACTIVE_TILE"
        android:value="true" />
</service>
```

## Configuration XMLs

### Accessibility Service Config

The file [accessibility_service_config.xml](file:///d:/project/android/snapshort/app/src/main/res/xml/accessibility_service_config.xml) defines:

| Attribute | Value | Purpose |
|-----------|-------|---------|
| `canTakeScreenshot` | `true` | **Required** - enables screenshot capture API |
| `accessibilityEventTypes` | `typeAllMask` | Broad coverage (unused) |
| `accessibilityFeedbackType` | `feedbackGeneric` | Standard feedback type |
| `settingsActivity` | `MainActivity` | Links back to app from settings |

> [!IMPORTANT]  
> The `android:canTakeScreenshot="true"` attribute is **mandatory** for the `takeScreenshot()` API to work. Without it, the system throws `SecurityException`.

## Building the Project

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Screenshot API**: Requires Android 11+ (API 30) for direct capture

### Key Dependencies
- `androidx.compose.*`: UI framework
- `io.coil-kt:coil-compose`: Image loading
- `androidx.lifecycle:lifecycle-runtime-compose`: Lifecycle-aware state

## Installation Steps

```bash
# Build and install
./gradlew installDebug

# View logs during capture
adb logcat -s ScreenshotAccessibility ScreenshotTileService ScreenshotRepository
```

1. Open the app and follow prompt to **Enable Accessibility Service**
2. Edit Quick Settings panel to add the **SnapShort** tile
3. Tap the tile to capture - screenshot saved directly to app gallery
