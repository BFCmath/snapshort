package com.example.snapshort.service

import android.accessibilityservice.AccessibilityService
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
import android.net.Uri
import com.example.snapshort.data.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenshotAccessibility"
        const val ACTION_TAKE_SCREENSHOT = "com.example.snapshort.ACTION_TAKE_SCREENSHOT"
        
        // Delay to allow notification shade to close before capture
        private const val SHADE_DISMISS_DELAY_MS = 450L
        
        private var instance: ScreenshotAccessibilityService? = null
        
        fun isServiceEnabled(): Boolean = instance != null
        
        /**
         * Take a screenshot after dismissing the notification shade.
         * This ensures the captured image shows the underlying app content,
         * not the Quick Settings panel.
         */
        fun takeScreenshot() {
            instance?.dismissAndCapture()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: ScreenshotRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TAKE_SCREENSHOT) {
                dismissAndCapture()
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
        serviceScope.cancel()
        Log.d(TAG, "Accessibility Service destroyed")
    }

    /**
     * Dismiss the notification shade first, then capture after a delay.
     * Uses GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE on Android 12+.
     */
    private fun dismissAndCapture() {
        Log.d(TAG, "Dismissing notification shade before capture...")
        
        // Dismiss notification shade using the proper accessibility action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31) - Use official global action
            val dismissed = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            Log.d(TAG, "GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE result: $dismissed")
        }
        // Note: For Android 11 (API 30), the shade will already be collapsed 
        // by the time our callback runs since takeScreenshot() is async
        
        // Wait for shade to animate closed, then capture
        handler.postDelayed({
            performScreenshot()
        }, SHADE_DISMISS_DELAY_MS)
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
                        
                        // Get the hardware bitmap
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        
                        if (hardwareBitmap != null) {
                            // Launch background coroutine for saving
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    // Convert to software bitmap for file operations
                                    // This copy operation can be heavy, so we do it in IO
                                    val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    
                                    // Make sure to close hardware resources
                                    // Note: We can't recycle hardwareBitmap from this thread if it was created
                                    // on another, but we can close the hardwareBuffer.
                                    // Actually, wrapHardwareBuffer returns a Bitmap that manages the buffer.
                                    // We should close the screenshot hardwareBuffer as per API.
                                    // However, since we are inside onSuccess which provides the result, 
                                    // let's do the copy then close everything safely.
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error copying bitmap", e)
                                    withContext(Dispatchers.Main) {
                                         Toast.makeText(this@ScreenshotAccessibilityService, "Error processing screenshot", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }
                                
                                // Actually, we need to handle the resources carefully. 
                                // The hardwareBitmap wraps the buffer.
                                // We should copy it, then close/recycle.
                                // NOTE: copy() might need to run before we close the buffer.
                                
                                val softwareBitmap = try {
                                    hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                } catch(e: Exception) { null }
                                
                                // Clean up hardware resources immediately
                                hardwareBitmap.recycle()
                                screenshot.hardwareBuffer.close()
                                
                                if (softwareBitmap != null) {
                                    // Save to internal storage
                                    val savedFile = repository.saveScreenshot(softwareBitmap)
                                    softwareBitmap.recycle()
                                    
                                    withContext(Dispatchers.Main) {
                                        if (savedFile != null) {
                                            Toast.makeText(
                                                this@ScreenshotAccessibilityService,
                                                "Screenshot saved!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            
                                            // Launch Image Preview
                                            val intent = Intent(this@ScreenshotAccessibilityService, com.example.snapshort.ui.PreviewActivity::class.java).apply {
                                                putExtra("IMAGE_URI", Uri.fromFile(savedFile).toString())
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            }
                                            startActivity(intent)
                                        } else {
                                            Toast.makeText(
                                                this@ScreenshotAccessibilityService,
                                                "Failed to save screenshot",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                         Toast.makeText(this@ScreenshotAccessibilityService, "Failed to process image", Toast.LENGTH_SHORT).show()
                                    }
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
