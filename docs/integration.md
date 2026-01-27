# Snapshort: Integration & Setup

This document provides the technical requirements and configuration details for integrating and setting up Snapshort's core services.

## Android Manifest Requirements

The [AndroidManifest.xml](file:///d:/project/android/snapshort/app/src/main/AndroidManifest.xml) is critical for declaring permissions and services.

### Permissions

| Permission | Protection Level | Purpose |
| :--- | :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Signature | Required to host an Accessibility Service. |
| `BIND_QUICK_SETTINGS_TILE` | Signature | Required to host a Quick Settings Tile. |
| `READ_MEDIA_IMAGES` | Dangerous | Required on Android 13+ (API 33+) to detect new screenshots via `FileObserver`. |
| `READ_EXTERNAL_STORAGE` | Dangerous | Legacy permission for Android 12 (API 32) and below. |

### Service Declarations

#### 1. Accessibility Service
Must be declared with the `BIND_ACCESSIBILITY_SERVICE` permission.

- **`android:permission`**: `android.permission.BIND_ACCESSIBILITY_SERVICE`
- **`meta-data`**: Links to `@xml/accessibility_service_config` which defines the `feedbackGeneric` type and `flagDefault`.
- **`exported="true"`**: Necessary for the Android system to bind to the service.

```xml
<service
    android:name=".service.ScreenshotAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

#### 2. Tile Service
Must be declared with the `BIND_QUICK_SETTINGS_TILE` permission.

- **`meta-data`**: `android.service.quicksettings.ACTIVE_TILE` is set to `true`. This tells the system that the tile manages its own state and doesn't need to be bound unless clicked or updated, saving battery.
- **`icon`**: Uses `@drawable/ic_screenshot`.

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
</service>
```

## Configuration XMLs

### Accessibility Service Config
The file [accessibility_service_config.xml](file:///d:/project/android/snapshort/app/src/main/res/xml/accessibility_service_config.xml) defines:

- **`accessibilityEventTypes`**: `typeAllMask` (though usually unused, it's set for broad coverage).
- **`notificationTimeout`**: `100ms`.
- **`settingsActivity`**: Points back to `MainActivity` so users can easily configure the app from the Accessibility settings screen.

## Building the Project

The project is built using Gradle with Kotlin DSL.

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Key Dependencies**:
    - `androidx.compose.*`: For the UI.
    - `io.coil-kt:coil-compose`: For image loading.
    - `androidx.lifecycle:lifecycle-runtime-compose`: For lifecycle-aware UI state.

## Installation Steps (Developer)

1. Run `./gradlew installDebug`.
2. Open the app and follow the prompt to **Enable Accessibility Service**.
3. Edit the Quick Settings panel to add the **SnapShort** tile.
4. (Optional) Check `adb logcat -s ScreenshotAccessibility` for debug logs during capture.
