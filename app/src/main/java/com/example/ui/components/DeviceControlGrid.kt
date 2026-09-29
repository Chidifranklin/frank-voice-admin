package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.DeviceDiagnostics
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DeviceControlGrid(
    flashlightOn: Boolean,
    onToggleFlashlight: () -> Unit,
    volumePercent: Float,
    onVolumeChange: (Float) -> Unit,
    deviceStats: DeviceDiagnostics?,
    onOpenSetting: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Hardware Row: Flashlight & Volume
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flashlight Super Admin Tile
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (flashlightOn) CyberAmber else CyberCardBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onToggleFlashlight() }
                    .testTag("flashlight_control_tile")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (flashlightOn) CyberAmber.copy(alpha = 0.2f) else Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (flashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Flashlight",
                                tint = if (flashlightOn) CyberAmber else TextMuted
                            )
                        }
                        Switch(
                            checked = flashlightOn,
                            onCheckedChange = { onToggleFlashlight() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAmber,
                                checkedTrackColor = CyberAmber.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color(0xFF0F172A)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Flashlight",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (flashlightOn) "ACTIVE / ON" else "STANDBY / OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (flashlightOn) CyberAmber else TextMuted
                    )
                }
            }

            // Audio Volume Super Admin Tile
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
                    .testTag("volume_control_tile")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (volumePercent == 0f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume",
                                tint = CyberCyan
                            )
                        }
                        Text(
                            text = "${(volumePercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "System Audio",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Slider(
                        value = volumePercent,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = Color(0xFF0F172A)
                        )
                    )
                }
            }
        }

        // System Settings Direct Super Admin Shortcuts Grid
        Text(
            text = "SUPER ADMIN SYSTEM SHORTCUTS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingShortcutButton(
                icon = Icons.Default.Wifi,
                label = "Wi-Fi",
                tint = CyberCyan,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSetting("WIFI") }
            )
            SettingShortcutButton(
                icon = Icons.Default.Bluetooth,
                label = "Bluetooth",
                tint = CyberCyan,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSetting("BLUETOOTH") }
            )
            SettingShortcutButton(
                icon = Icons.Default.BatteryStd,
                label = "Battery",
                tint = CyberEmerald,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSetting("BATTERY") }
            )
            SettingShortcutButton(
                icon = Icons.Default.BrightnessMedium,
                label = "Display",
                tint = CyberAmber,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSetting("DISPLAY") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingShortcutButton(
                icon = Icons.Default.SdCard,
                label = "Storage",
                tint = CyberEmerald,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSetting("STORAGE") }
            )
            SettingShortcutButton(
                icon = Icons.Default.Apps,
                label = "App Admin",
                tint = CyberCyan,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSetting("APP_DETAILS") }
            )
            SettingShortcutButton(
                icon = Icons.Default.Settings,
                label = "All Settings",
                tint = TextPrimary,
                modifier = Modifier.weight(2f),
                onClick = { onOpenSetting("ALL") }
            )
        }
    }
}

@Composable
fun SettingShortcutButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = TextPrimary
            )
        }
    }
}
