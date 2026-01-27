package com.example.snapshort.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.snapshort.data.ScreenshotRepository
import java.io.File

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

    private var screenshotObserver: FileObserver? = null
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
        
        // Configure the service
        serviceInfo = AccessibilityServiceInfo().apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK
            notificationTimeout = 100
        }
        
        // Register broadcast receiver
        val filter = IntentFilter(ACTION_TAKE_SCREENSHOT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }
        
        // Start observing screenshot directory
        startScreenshotObserver()
        
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
        screenshotObserver?.stopWatching()
        Log.d(TAG, "Accessibility Service destroyed")
    }

    private fun performScreenshot() {
        Log.d(TAG, "Taking screenshot...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val result = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            if (result) {
                Log.d(TAG, "Screenshot action performed successfully")
                handler.post {
                    Toast.makeText(this, "Screenshot taken!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "Screenshot action failed")
                handler.post {
                    Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            handler.post {
                Toast.makeText(this, "Screenshot requires Android 9+", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startScreenshotObserver() {
        val screenshotDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Screenshots"
        )
        
        if (!screenshotDir.exists()) {
            // Try alternative path
            val altPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "Screenshots"
            )
            if (altPath.exists()) {
                observeDirectory(altPath)
            }
        } else {
            observeDirectory(screenshotDir)
        }
    }

    private fun observeDirectory(directory: File) {
        screenshotObserver = object : FileObserver(directory.path, CREATE or CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg"))) {
                    Log.d(TAG, "New screenshot detected: $path")
                    
                    // Delay to ensure file is fully written
                    handler.postDelayed({
                        val sourceFile = File(directory, path)
                        if (sourceFile.exists()) {
                            val success = repository.copyScreenshotToInternal(sourceFile)
                            if (success) {
                                Log.d(TAG, "Screenshot copied to internal storage")
                            }
                        }
                    }, 1000)
                }
            }
        }
        screenshotObserver?.startWatching()
        Log.d(TAG, "Started observing: ${directory.path}")
    }
}
