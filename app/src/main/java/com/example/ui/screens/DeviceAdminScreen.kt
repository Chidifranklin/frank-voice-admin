package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.InstalledApp
import com.example.ui.OmniViewModel
import com.example.ui.components.AccessibilityControlCard
import com.example.ui.components.ActiveTimersStopwatchCard
import com.example.ui.components.DeviceControlGrid
import com.example.ui.components.HeyFrankBackgroundCard
import com.example.ui.components.NotificationReaderCard
import com.example.ui.components.VoicePersonaCustomizerCard
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DeviceAdminScreen(
    viewModel: OmniViewModel,
    modifier: Modifier = Modifier
) {
    val deviceStats by viewModel.deviceStats.collectAsState()
    val flashlightOn by viewModel.flashlightOn.collectAsState()
    val volumePercent by viewModel.volumePercent.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val appSearchQuery by viewModel.appSearchQuery.collectAsState()

    val isFrankRunning by viewModel.isFrankServiceRunning.collectAsState()
    val isFrankHotwordListening by viewModel.isFrankHotwordListening.collectAsState()
    val isContinuousMode by viewModel.isContinuousListeningMode.collectAsState()
    val isBatteryExempt by viewModel.isBatteryExempt.collectAsState()
    val isOverlayGranted by viewModel.isOverlayGranted.collectAsState()
    val frankStatusMessage by viewModel.frankStatusMessage.collectAsState()
    val lastFrankUtterance by viewModel.lastFrankUtterance.collectAsState()
    val voiceSettings by viewModel.voiceSettings.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
    ) {
        // Hey Frank Background Service & Persistent Notification Card
        item {
            HeyFrankBackgroundCard(
                isServiceRunning = isFrankRunning,
                isHotwordListening = isFrankHotwordListening,
                isContinuousMode = isContinuousMode,
                isMuteBeeps = voiceSettings.isMuteVoiceBeeps,
                isBatteryExempt = isBatteryExempt,
                isOverlayGranted = isOverlayGranted,
                statusMessage = frankStatusMessage,
                lastUtterance = lastFrankUtterance,
                onToggleService = { viewModel.toggleFrankService(it) },
                onToggleContinuousMode = { viewModel.toggleContinuousListeningMode(it) },
                onToggleMuteBeeps = { viewModel.updateMuteVoiceBeeps(it) },
                onRequestBatteryExemption = { viewModel.requestBatteryOptimizationExemption() },
                onRequestOverlayPermission = { viewModel.requestOverlayPermission() },
                onSimulateWakeWord = {
                    viewModel.onVoiceRecognitionResult("Hey Frank, system diagnostics")
                }
            )
        }

        // Global Accessibility Navigation & Gesture Controls Card
        item {
            AccessibilityControlCard(viewModel = viewModel)
        }

        // Hands-Free Notification Reader Card
        item {
            NotificationReaderCard(viewModel = viewModel)
        }

        // Active Countdown Timers & Precision Stopwatch Card
        item {
            ActiveTimersStopwatchCard(viewModel = viewModel)
        }

        // Voice Persona & Wake Word Customization Card
        item {
            VoicePersonaCustomizerCard(viewModel = viewModel)
        }

        // Super Admin Telemetry / Diagnostics Dashboard Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CyberEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SUPER ADMIN HARDWARE TELEMETRY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp
                                ),
                                color = CyberCyan
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshDiagnostics() },
                            modifier = Modifier.size(28.dp).testTag("refresh_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (deviceStats != null) {
                        val stats = deviceStats!!
                        // 4 telemetry metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Battery
                            TelemetryPill(
                                icon = if (stats.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                value = "${stats.batteryPercent}%",
                                label = if (stats.isCharging) "Charging" else "Battery",
                                tint = CyberEmerald
                            )

                            // RAM
                            val ramUsed = (stats.totalRamMb - stats.availableRamMb).coerceAtLeast(0)
                            TelemetryPill(
                                icon = Icons.Default.Memory,
                                value = "${ramUsed / 1024}GB",
                                label = "RAM / ${stats.totalRamMb / 1024}GB",
                                tint = CyberCyan
                            )

                            // Storage
                            TelemetryPill(
                                icon = Icons.Default.Storage,
                                value = "${stats.freeStorageGb}GB",
                                label = "Free Disk",
                                tint = CyberAmber
                            )

                            // Network
                            TelemetryPill(
                                icon = Icons.Default.Wifi,
                                value = stats.networkType,
                                label = if (stats.isOnline) "Online" else "Offline",
                                tint = if (stats.isOnline) CyberEmerald else CyberAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Device Info
                        Text(
                            text = "${stats.deviceModel} • ${stats.androidVersion}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Hardware Controls Grid (Flashlight, Volume, System Settings)
        item {
            DeviceControlGrid(
                flashlightOn = flashlightOn,
                onToggleFlashlight = { viewModel.toggleFlashlight() },
                volumePercent = volumePercent,
                onVolumeChange = { viewModel.setVolume(it) },
                deviceStats = deviceStats,
                onOpenSetting = { viewModel.openSystemSetting(it) }
            )
        }

        // Installed Applications Super Admin Control Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INSTALLED APPS SUPER ADMIN (${filteredApps.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { viewModel.setAppSearchQuery(it) },
                    placeholder = { Text("Search installed apps to launch...", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCard,
                        unfocusedContainerColor = CyberCard,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
            }
        }

        // List of Installed Apps
        items(filteredApps.take(30), key = { it.packageName }) { app ->
            InstalledAppRow(
                app = app,
                onLaunch = { viewModel.launchAppByPackage(app.packageName) }
            )
        }
    }
}

@Composable
fun TelemetryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextMuted
        )
    }
}

@Composable
fun InstalledAppRow(
    app: InstalledApp,
    onLaunch: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .testTag("app_item_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = "App Icon",
                        tint = if (app.isSystemApp) CyberAmber else CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        if (app.isSystemApp) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "SYSTEM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = CyberAmber
                                )
                            }
                        }
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }

            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan.copy(alpha = 0.15f),
                    contentColor = CyberCyan
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("launch_${app.packageName}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = "Launch",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Launch",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
