package com.example.snapshort

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.snapshort.data.ScreenshotRepository
import com.example.snapshort.ui.GalleryScreen
import com.example.snapshort.ui.theme.SnapshortTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapshortTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val screenshotRepo = remember { ScreenshotRepository(context) }
    val metadataRepo = remember { com.example.snapshort.data.MetadataRepository(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var allScreenshots by remember { mutableStateOf<List<File>>(emptyList()) }
    var allMetadata by remember { mutableStateOf<List<com.example.snapshort.data.ScreenshotMetadata>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Navigation State
    var currentTab by remember { mutableStateOf(Tab.Snaps) }

    // Check accessibility and load data
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
            allScreenshots = screenshotRepo.getScreenshots()
            allMetadata = metadataRepo.getAllMetadata()
            
            // Check for Auto-Nav signal
            val prefs = context.getSharedPreferences("snapshort_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("NAVIGATE_TO_TASKS", false)) {
                currentTab = Tab.Tasks
                prefs.edit().putBoolean("NAVIGATE_TO_TASKS", false).apply()
            }
        }
    }

    // Filter Logic
    val snaps = remember(allScreenshots, allMetadata) {
        allScreenshots.filter { file -> 
            val meta = allMetadata.find { it.fileName == file.name }
            // Snap if no metadata, OR if metadata has no Task fields
            if (meta == null) true
            else meta.displayName.isNullOrBlank() && meta.dueDate == null && meta.description.isNullOrBlank()
        }
    }
    
    val tasks = remember(allMetadata) {
        allMetadata.filter { 
            !it.displayName.isNullOrBlank() || it.dueDate != null || !it.description.isNullOrBlank()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isAccessibilityEnabled) {
            AccessibilityPermissionScreen(
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Filled.Home, contentDescription = "Snaps") },
                            label = { Text("Snaps") },
                            selected = currentTab == Tab.Snaps,
                            onClick = { currentTab = Tab.Snaps }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Filled.DateRange, contentDescription = "Tasks") },
                            label = { Text("Tasks") },
                            selected = currentTab == Tab.Tasks,
                            onClick = { currentTab = Tab.Tasks }
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentTab) {
                        Tab.Snaps -> {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    allScreenshots = screenshotRepo.getScreenshots()
                                    scope.launch { allMetadata = metadataRepo.getAllMetadata() } // Refresh both
                                    isRefreshing = false
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                GalleryScreen(
                                    screenshots = snaps,
                                    onRefresh = { 
                                        allScreenshots = screenshotRepo.getScreenshots()
                                        scope.launch { allMetadata = metadataRepo.getAllMetadata() }
                                    },
                                    onDelete = { files ->
                                        files.forEach { file ->
                                            screenshotRepo.deleteScreenshot(file)
                                        }
                                        allScreenshots = screenshotRepo.getScreenshots()
                                    }
                                )
                            }
                        }
                        Tab.Tasks -> {
                            com.example.snapshort.ui.TasksScreen(
                                tasks = tasks,
                                getScreenshotFile = { fileName -> 
                                    allScreenshots.find { it.name == fileName } 
                                },
                                onToggleComplete = { task, isCompleted ->
                                    scope.launch {
                                        metadataRepo.saveMetadata(task.copy(isCompleted = isCompleted))
                                        allMetadata = metadataRepo.getAllMetadata()
                                    }
                                },
                                onTaskClick = { task ->
                                    val intent = Intent(context, com.example.snapshort.ui.EditScreenshotActivity::class.java).apply {
                                        // Pass image URI if file exists
                                        val file = allScreenshots.find { it.name == task.fileName }
                                        if (file != null) {
                                            putExtra("IMAGE_URI", android.net.Uri.fromFile(file).toString())
                                        }
                                        // TODO: Pass Task ID to load existing metadata in Editor
                                        putExtra("TASK_ID", task.id)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class Tab { Snaps, Tasks }

@Composable
private fun AccessibilityPermissionScreen(
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Accessibility Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "SnapShort needs accessibility permission to take screenshots when you tap the Quick Settings tile.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = onOpenSettings) {
                    Text("Open Accessibility Settings")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Find \"SnapShort\" in the list and enable it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    
    return enabledServices.any { serviceInfo ->
        serviceInfo.resolveInfo.serviceInfo.packageName == context.packageName
    }
}