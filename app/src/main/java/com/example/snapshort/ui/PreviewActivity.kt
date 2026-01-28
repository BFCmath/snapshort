package com.example.snapshort.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.snapshort.ui.theme.SnapshortTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.core.view.WindowCompat

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val imageUriString = intent.getStringExtra("IMAGE_URI") ?: run {
            finish()
            return
        }
        val imageUri = Uri.parse(imageUriString)

        setContent {
            SnapshortTheme {
                PreviewScreen(
                    imageUri = imageUri,
                    onDismiss = { finish() },
                    onEdit = {
                        val editIntent = Intent(this, EditScreenshotActivity::class.java).apply {
                            putExtra("IMAGE_URI", imageUriString)
                        }
                        startActivity(editIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PreviewScreen(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    
    // Offset for the slide animation (0 = showing, -screenWidth = hidden left)
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // State to track if user is interacting, to pause auto-dismiss
    var isUserInteracting by remember { mutableStateOf(false) }

    // Auto-dismiss logic
    LaunchedEffect(isUserInteracting) {
        if (!isUserInteracting) {
            // Wait 1 second
            delay(2500)
            // Animate off-screen to the left
            offsetX.animateTo(
                targetValue = -screenWidthPx,
                animationSpec = tween(durationMillis = 300)
            )
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(120.dp, 200.dp) // Thumbnail size
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            isUserInteracting = true 
                        },
                        onDragEnd = { 
                            scope.launch {
                                // If dragged significantly to the left, dismiss
                                if (offsetX.value < -150f) {
                                    offsetX.animateTo(
                                        targetValue = -screenWidthPx,
                                        animationSpec = tween(durationMillis = 300)
                                    )
                                    onDismiss()
                                } else {
                                    // Snap back and resume auto-dismiss behavior
                                    offsetX.animateTo(0f)
                                    isUserInteracting = false 
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                                isUserInteracting = false
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // Apply drag, limiting how far right they can pull (resistance)
                            val newOffset = offsetX.value + dragAmount
                            if (newOffset <= 50f) { // Allow slight overdrag to right
                                offsetX.snapTo(newOffset)
                            }
                        }
                    }
                }
                .clickable { onEdit() },
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "Screenshot Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
