package com.example.snapshort.service

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.snapshort.R

class ScreenshotTileService : TileService() {

    companion object {
        private const val TAG = "ScreenshotTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        Log.d(TAG, "Tile clicked")
        
        if (!ScreenshotAccessibilityService.isServiceEnabled()) {
            // Service not enabled, show toast and don't proceed
            Log.w(TAG, "Accessibility service not enabled")
            showDialog(createAccessibilityDialog())
            return
        }

        // Collapse the quick settings panel before taking screenshot
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ way
            collapseShade()
        } else {
            // Legacy way - send a broadcast
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        }

        // Small delay to allow panel to collapse
        android.os.Handler(mainLooper).postDelayed({
            triggerScreenshot()
        }, 300)
    }

    private fun collapseShade() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapse = statusBarManager.getMethod("collapsePanels")
            collapse.invoke(statusBarService)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collapse shade", e)
        }
    }

    private fun triggerScreenshot() {
        // Direct call to the accessibility service
        ScreenshotAccessibilityService.takeScreenshot()
        Log.d(TAG, "Screenshot triggered")
    }

    private fun createAccessibilityDialog(): android.app.AlertDialog {
        return android.app.AlertDialog.Builder(this)
            .setTitle("Enable Accessibility")
            .setMessage("Please enable SnapShort accessibility service to take screenshots.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            if (ScreenshotAccessibilityService.isServiceEnabled()) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "SnapShort"
                tile.contentDescription = "Take screenshot"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "SnapShort"
                tile.contentDescription = "Enable accessibility service first"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (ScreenshotAccessibilityService.isServiceEnabled()) "Ready" else "Disabled"
            }
            
            tile.updateTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added")
        updateTileState()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "Tile removed")
    }
}
