package com.example.snapshort.service

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

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
            Log.w(TAG, "Accessibility service not enabled")
            showDialog(createAccessibilityDialog())
            return
        }

        // Trigger screenshot - the AccessibilityService handles dismissing 
        // the notification shade before capturing
        ScreenshotAccessibilityService.takeScreenshot()
        Log.d(TAG, "Screenshot triggered")
    }

    private fun createAccessibilityDialog(): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("Enable Accessibility")
            .setMessage("Please enable SnapShort accessibility service to take screenshots.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
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
