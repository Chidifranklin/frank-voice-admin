package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.VoiceActivationService
import com.example.ui.MainTab
import com.example.ui.OmniViewModel
import com.example.ui.screens.AuditPrivacyScreen
import com.example.ui.screens.DeviceAdminScreen
import com.example.ui.screens.FileWorkspaceScreen
import com.example.ui.screens.MemoryVaultScreen
import com.example.ui.screens.VoiceAssistantScreen
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                OmniAppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniAppRoot(viewModel: OmniViewModel = viewModel()) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val memoryCount by viewModel.memoryCount.collectAsState()
    val activeReminderCount by viewModel.activeReminderCount.collectAsState()
    var showRemindersSheet by remember { mutableStateOf(false) }

    // Request audio and notification permissions for background voice activation
    val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioOk) {
            VoiceActivationService.start(context)
            VoiceActivationService.restartListeningNow()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryStatus()
        viewModel.refreshOverlayStatus()
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            permissionsLauncher.launch(permissionsToRequest)
        } else {
            VoiceActivationService.start(context)
            VoiceActivationService.restartListeningNow()
        }
    }

    // Refresh battery optimization and overlay status and notify background voice service
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refreshBatteryStatus()
                    viewModel.refreshOverlayStatus()
                    VoiceActivationService.setAppInForeground(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    VoiceActivationService.setAppInForeground(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = CyberBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Glowing status indicator
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(CyberEmerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Frank",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "SUPER ADMIN • ON-DEVICE PRIVACY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CyberCyan
                            )
                        }
                    }
                },
                actions = {
                    // Audio Playing Status Pill
                    if (playerState.isPlaying) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(CyberCard)
                                .border(1.dp, CyberCyan, CircleShape)
                                .clickable { viewModel.toggleMusic() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio playing",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Focus Audio",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = CyberCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Memory Vault Counter Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CyberCard)
                            .border(1.dp, CyberCardBorder, CircleShape)
                            .clickable { viewModel.selectTab(MainTab.MEMORY_VAULT) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Memories",
                            tint = CyberEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$memoryCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = CyberEmerald
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Reminders & Alarms Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CyberCard)
                            .border(1.dp, if (activeReminderCount > 0) CyberCyan else CyberCardBorder, CircleShape)
                            .clickable { showRemindersSheet = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Reminders & Alarms",
                            tint = if (activeReminderCount > 0) CyberCyan else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$activeReminderCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (activeReminderCount > 0) CyberCyan else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberBg
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CyberCard,
                contentColor = TextPrimary,
                modifier = Modifier
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberCyan,
                    selectedTextColor = CyberCyan,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = CyberCyan.copy(alpha = 0.15f)
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.ASSISTANT,
                    onClick = { viewModel.selectTab(MainTab.ASSISTANT) },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Voice Assistant") },
                    label = { Text("Voice AI", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_tab_assistant")
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.MEMORY_VAULT,
                    onClick = { viewModel.selectTab(MainTab.MEMORY_VAULT) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Memory Vault") },
                    label = { Text("Memory", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_tab_memory")
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.DEVICE_ADMIN,
                    onClick = { viewModel.selectTab(MainTab.DEVICE_ADMIN) },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Device Admin") },
                    label = { Text("Device", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_tab_device")
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.FILE_WORKSPACE,
                    onClick = { viewModel.selectTab(MainTab.FILE_WORKSPACE) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                    label = { Text("Files", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_tab_files")
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.AUDIT_PRIVACY,
                    onClick = { viewModel.selectTab(MainTab.AUDIT_PRIVACY) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Privacy Audit") },
                    label = { Text("Privacy", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_tab_audit")
                )
            }
        }
    ) { padding ->
        Crossfade(
            targetState = currentTab,
            label = "tab_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { tab ->
            when (tab) {
                MainTab.ASSISTANT -> VoiceAssistantScreen(viewModel = viewModel)
                MainTab.MEMORY_VAULT -> MemoryVaultScreen(viewModel = viewModel)
                MainTab.DEVICE_ADMIN -> DeviceAdminScreen(viewModel = viewModel)
                MainTab.FILE_WORKSPACE -> FileWorkspaceScreen(viewModel = viewModel)
                MainTab.AUDIT_PRIVACY -> AuditPrivacyScreen(viewModel = viewModel)
            }
        }
    }

    if (showRemindersSheet) {
        com.example.ui.components.RemindersSheet(
            viewModel = viewModel,
            onDismiss = { showRemindersSheet = false }
        )
    }
}
