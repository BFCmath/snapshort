package com.example.snapshort.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ScreenshotRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenshotRepository"
        private const val SCREENSHOTS_DIR = "screenshots"
    }
    
    private val screenshotsDir: File by lazy {
        File(context.filesDir, SCREENSHOTS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    fun getScreenshots(): List<File> {
        return screenshotsDir.listFiles()
            ?.filter { it.extension in listOf("png", "jpg", "jpeg") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
    fun copyScreenshotToInternal(sourceFile: File): Boolean {
        return try {
            val destFile = File(screenshotsDir, "screenshot_${System.currentTimeMillis()}.png")
            
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Screenshot saved: ${destFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy screenshot", e)
            false
        }
    }
    
    fun saveScreenshot(bitmap: Bitmap): File? {
        return try {
            val file = File(screenshotsDir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            Log.d(TAG, "Screenshot saved: ${file.name}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            null
        }
    }
    
    suspend fun loadBitmap(file: File): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap", e)
            null
        }
    }
    
    fun deleteScreenshot(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete screenshot", e)
            false
        }
    }
    
    fun deleteAllScreenshots() {
        screenshotsDir.listFiles()?.forEach { it.delete() }
    }
}
