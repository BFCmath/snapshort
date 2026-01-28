package com.example.snapshort.data

data class ScreenshotMetadata(
    val id: String,
    val fileName: String,
    val displayName: String? = null,
    val dueDate: Long? = null,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val creationDate: Long
)
