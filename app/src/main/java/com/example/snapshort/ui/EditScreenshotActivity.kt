package com.example.snapshort.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView
import com.example.snapshort.data.MetadataRepository
import com.example.snapshort.data.ScreenshotMetadata
import com.example.snapshort.data.ScreenshotRepository
import com.example.snapshort.ui.theme.SnapshortTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EditScreenshotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val imageUriString = intent.getStringExtra("IMAGE_URI") ?: run {
            finish()
            return
        }
        val imageUri = Uri.parse(imageUriString)

        setContent {
            SnapshortTheme {
                EditScreen(
                    imageUri = imageUri,
                    onSave = { finish() },
                    onDelete = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    imageUri: Uri,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metadataRepository = remember { MetadataRepository(context) }
    val screenshotRepository = remember { ScreenshotRepository(context) }
    
    var name by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var description by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Use a state for the view to ensure we don't recreate it unnecessarily
    // but also don't initialize it synchronously if it's heavy.
    // However, AndroidView factory is usually safe. The main issue was likely
    // setImageUriAsync being called too aggressively or blocking logic elsewhere.
    
    val cropImageView = remember { 
        CropImageView(context).apply {
            isShowCropOverlay = false // Hidden initially
            isAutoZoomEnabled = false // Disable zoom (freeze image)
            // Initial settings
            setAspectRatio(1, 1)
            setFixedAspectRatio(false)
            
            // Custom Touch Listener for Drag-to-Create
            setOnTouchListener { v, event ->
                // Always handle touches to enforce "Draw New" behavior
                // We do NOT return false here, even if overlay is visible.

                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // Start a new crop selection
                        // We use the tag to store the start point (simple state tracking)
                        v.tag = android.graphics.PointF(event.x, event.y)
                        return@setOnTouchListener true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val startPoint = v.tag as? android.graphics.PointF ?: return@setOnTouchListener false
                        
                        // User is dragging, show the overlay now
                        if (!isShowCropOverlay) isShowCropOverlay = true
                        
                        // Calculate Crop Rect in Image Coordinates
                        val wholeImageRect = wholeImageRect
                        if (wholeImageRect != null) {
                            val viewWidth = width.toFloat()
                            val viewHeight = height.toFloat()
                            val imgWidth = wholeImageRect.width().toFloat()
                            val imgHeight = wholeImageRect.height().toFloat()
                            
                            // Calculate Scale and Offset (FitCenter logic)
                            val scale = minOf(viewWidth / imgWidth, viewHeight / imgHeight)
                            val offsetX = (viewWidth - imgWidth * scale) / 2f
                            val offsetY = (viewHeight - imgHeight * scale) / 2f
                            
                            // Map Touch -> Image
                            fun mapToImage(vx: Float, vy: Float): android.graphics.PointF {
                                val ix = (vx - offsetX) / scale
                                val iy = (vy - offsetY) / scale
                                return android.graphics.PointF(
                                    ix.coerceIn(0f, imgWidth), 
                                    iy.coerceIn(0f, imgHeight)
                                )
                            }
                            
                            val startImg = mapToImage(startPoint.x, startPoint.y)
                            val endImg = mapToImage(event.x, event.y)
                            
                            val left = minOf(startImg.x, endImg.x).toInt()
                            val top = minOf(startImg.y, endImg.y).toInt()
                            val right = maxOf(startImg.x, endImg.x).toInt()
                            val bottom = maxOf(startImg.y, endImg.y).toInt()
                            
                            // Logically valid rect?
                            if (right > left && bottom > top) {
                                cropRect = android.graphics.Rect(left, top, right, bottom)
                            }
                        }
                        return@setOnTouchListener true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        v.tag = null
                        return@setOnTouchListener true
                    }
                }
                false
            }
        } 
    }
    
    LaunchedEffect(imageUri) {
        isLoading = true
        // Offload any potential heavy URI resolution to IO dispatcher if needed,
        // though setImageUriAsync handles its own background work.
        // We'll add a small delay to let the UI settle if needed, or just call it.
        try {
            cropImageView.setImageUriAsync(imageUri)
            // We can't easily know when it's done without a listener, 
            // but setting it async avoids the immediate block.
            // Let's assume loading finishes quickly enough or the view handles it.
            // For better UX, we could use setOnSetImageUriCompleteListener
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Screenshot") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val file = File(imageUri.path ?: "")
                            screenshotRepository.deleteScreenshot(file) 
                            onDelete()
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                    IconButton(onClick = {
                        isLoading = true
                        scope.launch {
                            val croppedBitmap = cropImageView.croppedImage
                            if (croppedBitmap != null) {
                                val file = File(imageUri.path ?: "")
                                FileOutputStream(file).use { out ->
                                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                
                                // Snap-to-Task Logic
                                val hasName = name.isNotBlank()
                                val hasDate = dueDate != null
                                val hasDesc = description.isNotBlank()
                                val isTask = hasName || hasDate || hasDesc
                                
                                val metadata = ScreenshotMetadata(
                                    id = UUID.randomUUID().toString(),
                                    fileName = file.name,
                                    displayName = if (hasName) name else null,
                                    dueDate = dueDate,
                                    description = if (hasDesc) description else null,
                                    creationDate = System.currentTimeMillis()
                                )
                                metadataRepository.saveMetadata(metadata)                                

                                if (isTask) {
                                    // Signal Auto-Nav only if it's a real Task
                                    context.getSharedPreferences("snapshort_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("NAVIGATE_TO_TASKS", true)
                                        .apply()
                                        
                                    Toast.makeText(context, "Saved as Task!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Saved as Snap!", Toast.LENGTH_SHORT).show()
                                }
                                
                                isLoading = false
                                onSave()
                            } else {
                                isLoading = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Done, "Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Crop View
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { cropImageView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Metadata Form
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Task Name (Optional)") },
                        placeholder = { Text("Enter name or leave empty") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dueDate?.let { 
                                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) 
                            } ?: "",
                            onValueChange = {},
                            label = { Text("Due Date") },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDatePicker = true },
                            enabled = false, 
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, "Select Date")
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
            
            if (isLoading) {
                 // Simple loading overlay
                 Box(
                     modifier = Modifier
                         .fillMaxSize()
                         .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                     contentAlignment = Alignment.Center
                 ) {
                     Text("Processing...", style = MaterialTheme.typography.bodyLarge)
                 }
            }
        }
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
