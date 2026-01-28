package com.example.snapshort.data

data class ScreenshotMetadata(
    val id: String,
    val fileName: String,
    val displayName: String,
    val dueDate: Long? = null,
    val creationDate: Long
)
