package com.example.snapshort.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class MetadataRepository(private val context: Context) {

    companion object {
        private const val METADATA_FILE = "metadata.json"
    }

    private val gson = Gson()
    private val metadataFile: File by lazy {
        File(context.filesDir, METADATA_FILE)
    }

    suspend fun saveMetadata(metadata: ScreenshotMetadata) = withContext(Dispatchers.IO) {
        val currentList = getAllMetadata().toMutableList()
        currentList.removeAll { it.id == metadata.id }
        currentList.add(metadata)
        saveList(currentList)
    }

    suspend fun getMetadata(id: String): ScreenshotMetadata? = withContext(Dispatchers.IO) {
        getAllMetadata().find { it.id == id }
    }

    suspend fun deleteMetadata(id: String) = withContext(Dispatchers.IO) {
        val currentList = getAllMetadata().toMutableList()
        currentList.removeAll { it.id == id }
        saveList(currentList)
    }

    suspend fun getAllMetadata(): List<ScreenshotMetadata> = withContext(Dispatchers.IO) {
        if (!metadataFile.exists()) return@withContext emptyList()
        
        try {
            FileReader(metadataFile).use { reader ->
                val type = object : TypeToken<List<ScreenshotMetadata>>() {}.type
                return@use gson.fromJson(reader, type) ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveList(list: List<ScreenshotMetadata>) {
        try {
            FileWriter(metadataFile).use { writer ->
                gson.toJson(list, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
