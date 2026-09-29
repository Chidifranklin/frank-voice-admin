package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.SpeechTtsManager
import com.example.service.VoiceActivationService
import com.example.ui.ChatMessage
import com.example.ui.OmniViewModel
import com.example.ui.components.GeminiLiveConversationCard
import com.example.ui.components.HeyFrankBackgroundCard
import com.example.ui.components.SearchGroundingSourcesRow
import com.example.ui.components.VoiceWaveformHud
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceAssistantScreen(
    viewModel: OmniViewModel,
    modifier: Modifier = Modifier
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isTtsSpeaking by viewModel.ttsManager.isSpeaking.collectAsState()
    val playerState by viewModel.playerState.collectAsState()

    // Background Frank Voice Activation states
    val isFrankRunning by viewModel.isFrankServiceRunning.collectAsState()
    val isFrankHotwordListening by viewModel.isFrankHotwordListening.collectAsState()
    val isContinuousMode by viewModel.isContinuousListeningMode.collectAsState()
    val isBatteryExempt by viewModel.isBatteryExempt.collectAsState()
    val isOverlayGranted by viewModel.isOverlayGranted.collectAsState()
    val frankStatusMessage by viewModel.frankStatusMessage.collectAsState()
    val lastFrankUtterance by viewModel.lastFrankUtterance.collectAsState()
    val activeReminders by viewModel.activeReminders.collectAsState()
    val voiceSettings by viewModel.voiceSettings.collectAsState()

    // Gemini 3.8 Live API states
    val liveState by viewModel.liveConnectionState.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val isLiveSpeaking by viewModel.isLiveSpeaking.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Android Speech Recognizer Intent Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = spoken?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                viewModel.onVoiceRecognitionResult(recognizedText)
            }
        }
    }

    fun startVoiceListening() {
        try {
            voiceLauncher.launch(SpeechTtsManager.createSpeechIntent())
        } catch (_: ActivityNotFoundException) {
            // Speech services not installed in emulator -> provide simulated voice prompt trigger
            inputPrompt = "What is my sister's birthday?"
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickChips = listOf(
        "Hey Frank, close active app",
        "Hey Frank, close background apps",
        "Hey Frank, close Spotify",
        "Hey Frank, allow internet search",
        "Hey Frank, deny internet search",
        "Hey Frank, go home",
        "Hey Frank, read my notifications",
        "Hey Frank, set a timer for 5 minutes",
        "Hey Frank, take a photo",
        "Hey Frank, what is 45 times 87",
        "Hey Frank, convert 10 miles to km",
        "Hey Frank, start stopwatch",
        "Hey Frank, scroll down",
        "Hey Frank, open notifications",
        "Hey Frank, take screenshot",
        "Hey Frank, remind me in 10 minutes to drink water",
        "Hey Frank, list files",
        "Hey Frank, create folder Projects",
        "Hey Frank, play focus music",
        "Hey Frank, turn on flashlight",
        "Hey Frank, system diagnostics"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Background Service Indicator Card ("Hey Frank" hotword & background listener)
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
            },
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // Gemini 3.8 Live API Card (Real-time voice conversations)
        GeminiLiveConversationCard(
            liveState = liveState,
            isSpeaking = isLiveSpeaking,
            liveTranscript = liveTranscript,
            onStartSession = { viewModel.startLiveSession() },
            onEndSession = { viewModel.endLiveSession() },
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Holographic Voice Waveform HUD
        VoiceWaveformHud(
            isListening = isFrankHotwordListening || (isFrankRunning && isContinuousMode) || isListening,
            isSpeaking = isTtsSpeaking,
            isPlayingMusic = playerState.isPlaying,
            onOrbClick = {
                viewModel.restartVoiceListening()
            }
        )

        // Quick Suggestion Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickChips) { chip ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CyberCard)
                        .border(1.dp, CyberCardBorder, CircleShape)
                        .clickable {
                            viewModel.executeNaturalLanguageCommand(chip)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = CyberCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mini Music Player Bar (if music active)
        AnimatedVisibility(visible = playerState.isPlaying || playerState.currentTrack != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerState.currentTrack?.title ?: "Ambient Focus",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${playerState.currentTrack?.genre} • 100% On-Device Audio",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = CyberCyan
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleMusic() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = CyberCyan
                            )
                        }
                        IconButton(
                            onClick = { viewModel.nextTrack() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Active Reminders strip (if any scheduled alarms exist)
        AnimatedVisibility(visible = activeReminders.isNotEmpty()) {
            val topReminder = activeReminders.firstOrNull()
            if (topReminder != null) {
                val deltaMs = topReminder.triggerTimeMillis - System.currentTimeMillis()
                val deltaMin = (deltaMs / (60 * 1000)).coerceAtLeast(1)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Active Reminder",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = topReminder.title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Alarm set • due in $deltaMin min (${activeReminders.size} active)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = CyberEmerald
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.cancelReminder(topReminder.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Conversation History Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(chatMessages, key = { index, msg -> "${msg.id}_$index" }) { _, msg ->
                ChatBubble(
                    message = msg,
                    onSpeak = { viewModel.ttsManager.speak(msg.text) }
                )
            }

            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Super Admin executing on-device command...",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                }
            }
        }

        // Input Field & Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = {
                    Text(
                        "Command or ask Frank...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("voice_command_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CyberCard,
                    unfocusedContainerColor = CyberCard,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = CyberCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputPrompt.isNotBlank()) {
                        val text = inputPrompt
                        inputPrompt = ""
                        viewModel.executeNaturalLanguageCommand(text)
                    }
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Mic or Send Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputPrompt.isNotBlank()) CyberCyan else CyberCard
                    )
                    .border(
                        1.dp,
                        if (isFrankRunning) CyberEmerald else CyberCyan,
                        CircleShape
                    )
                    .clickable {
                        if (inputPrompt.isNotBlank()) {
                            val text = inputPrompt
                            inputPrompt = ""
                            viewModel.executeNaturalLanguageCommand(text)
                        } else {
                            if (!isFrankRunning) {
                                viewModel.toggleFrankService(true)
                            } else {
                                viewModel.restartVoiceListening()
                            }
                        }
                    }
                    .testTag("send_command_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (inputPrompt.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                    contentDescription = "Submit Command",
                    tint = if (inputPrompt.isNotBlank()) Color(0xFF0F172A) else if (isFrankRunning) CyberEmerald else CyberCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onSpeak: () -> Unit
) {
    val isUser = message.sender == "USER"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser && message.actionBadge != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp, start = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CyberCyan.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = message.actionBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CyberCyan
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(listOf(CyberCyan, Color(0xFF0284C7)))
                    else Brush.linearGradient(listOf(CyberCard, Color(0xFF1E293B)))
                )
                .border(
                    1.dp,
                    if (isUser) Color.Transparent else CyberCardBorder,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isUser) Color(0xFF090D16) else TextPrimary,
                        fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal
                    )
                )

                if (!isUser) {
                    if (message.searchSources.isNotEmpty()) {
                        SearchGroundingSourcesRow(
                            sources = message.searchSources,
                            queries = emptyList(),
                            onOpenUrl = { /* Web url intent */ }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.clickable { onSpeak() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = CyberCyan.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play voice",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = CyberCyan.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
