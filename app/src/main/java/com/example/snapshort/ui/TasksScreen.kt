package com.example.snapshort.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.snapshort.data.ScreenshotMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    tasks: List<ScreenshotMetadata>,
    getScreenshotFile: (String) -> File?,
    onToggleComplete: (ScreenshotMetadata, Boolean) -> Unit,
    onTaskClick: (ScreenshotMetadata) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My Tasks") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        if (tasks.isEmpty()) {
            EmptyTasksMessage()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Sort: Incomplete first, then by Due Date (earliest first), then creation date
                val sortedTasks = tasks.sortedWith(
                    compareBy<ScreenshotMetadata> { it.isCompleted }
                        .thenBy { it.dueDate ?: Long.MAX_VALUE }
                        .thenByDescending { it.creationDate }
                )

                items(sortedTasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        imageFile = getScreenshotFile(task.fileName),
                        onToggleComplete = { isChecked -> onToggleComplete(task, isChecked) },
                        onClick = { onTaskClick(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📝",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "No active tasks",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Assign tasks to your snaps to see them here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun TaskItem(
    task: ScreenshotMetadata,
    imageFile: File?,
    onToggleComplete: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val isOverdue = task.dueDate != null && task.dueDate < System.currentTimeMillis()
    val isNearDue = task.dueDate != null && (task.dueDate - System.currentTimeMillis()) < 24 * 60 * 60 * 1000 // 24 hours

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (task.isCompleted) 0.6f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onToggleComplete
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail
            Card(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (imageFile != null && imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Task thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.displayName ?: "Untitled Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )

                if (task.dueDate != null) {
                    Text(
                        text = formatDate(task.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            task.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                            isOverdue -> MaterialTheme.colorScheme.error // Red if overdue
                            isNearDue -> MaterialTheme.colorScheme.error // Red if near due
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontWeight = if (!task.isCompleted && (isOverdue || isNearDue)) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            
            // Description Icon (if desc exists)
             if (!task.description.isNullOrEmpty()) {
                 Icon(
                     imageVector = Icons.Filled.Info,
                     contentDescription = "Has note",
                     tint = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.size(16.dp)
                 )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
