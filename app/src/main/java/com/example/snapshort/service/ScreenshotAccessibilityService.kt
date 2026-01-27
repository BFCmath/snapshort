package com.example.snapshort.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.snapshort.data.ScreenshotRepository

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenshotAccessibility"
        const val ACTION_TAKE_SCREENSHOT = "com.example.snapshort.ACTION_TAKE_SCREENSHOT"
        
        private var instance: ScreenshotAccessibilityService? = null
        
        fun isServiceEnabled(): Boolean = instance != null
        
        fun takeScreenshot() {
            instance?.performScreenshot()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: ScreenshotRepository

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TAKE_SCREENSHOT) {
                performScreenshot()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        repository = ScreenshotRepository(this)
        
        // Note: Do NOT override serviceInfo here as it would discard XML config
        // including canTakeScreenshot capability. The XML config is already applied.
        
        // Register broadcast receiver
        val filter = IntentFilter(ACTION_TAKE_SCREENSHOT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }
        
        Log.d(TAG, "Accessibility Service connected")
        Toast.makeText(this, "SnapShort service enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            unregisterReceiver(screenshotReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        Log.d(TAG, "Accessibility Service destroyed")
    }

    private fun performScreenshot() {
        Log.d(TAG, "Taking screenshot...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Use takeScreenshot API for direct bitmap capture
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                getMainExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        Log.d(TAG, "Screenshot captured successfully")
                        
                        // Get the hardware bitmap and convert to software bitmap for saving
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        
                        if (hardwareBitmap != null) {
                            // Convert to software bitmap for file operations
                            val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBitmap.recycle()
                            screenshot.hardwareBuffer.close()
                            
                            // Save to internal storage
                            val savedFile = repository.saveScreenshot(softwareBitmap)
                            softwareBitmap.recycle()
                            
                            handler.post {
                                if (savedFile != null) {
                                    Toast.makeText(
                                        this@ScreenshotAccessibilityService,
                                        "Screenshot saved!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@ScreenshotAccessibilityService,
                                        "Failed to save screenshot",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            Log.e(TAG, "Failed to wrap hardware buffer")
                            screenshot.hardwareBuffer.close()
                            handler.post {
                                Toast.makeText(
                                    this@ScreenshotAccessibilityService,
                                    "Screenshot capture failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                        handler.post {
                            val errorMessage = when (errorCode) {
                                1 -> "Cannot capture secure content"
                                2 -> "Accessibility permission issue"
                                else -> "Screenshot failed (error: $errorCode)"
                            }
                            Toast.makeText(
                                this@ScreenshotAccessibilityService,
                                errorMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9-10 - Fallback to performGlobalAction
            val result = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            handler.post {
                if (result) {
                    Toast.makeText(this, "Screenshot taken (check Photos app)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            handler.post {
                Toast.makeText(this, "Screenshot requires Android 9+", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
