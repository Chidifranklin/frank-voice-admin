package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.GeminiLiveClient
import com.example.service.SearchSourceItem
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Visual Banner & Controller for Gemini 3.8 Live API Voice Conversations
 */
@Composable
fun GeminiLiveConversationCard(
    liveState: GeminiLiveClient.LiveConnectionState,
    isSpeaking: Boolean,
    liveTranscript: String,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = liveState == GeminiLiveClient.LiveConnectionState.CONNECTED
    val isConnecting = liveState == GeminiLiveClient.LiveConnectionState.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeaking) 1.25f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 600 else 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isConnected) CyberCyan.copy(alpha = 0.8f) else CyberCardBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(if (isConnected) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected && isSpeaking -> CyberAmber
                                    isConnected -> CyberCyan
                                    isConnecting -> CyberAmber.copy(alpha = 0.5f)
                                    else -> Color(0xFF1E293B)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Live Voice",
                            tint = if (isConnected) Color(0xFF0B132B) else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gemini 3.8 Live API",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "VOICE CONVERSATION",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyberCyan
                                )
                            }
                        }
                        Text(
                            text = when (liveState) {
                                GeminiLiveClient.LiveConnectionState.CONNECTED ->
                                    if (isSpeaking) "Frank is speaking (Gemini 3.8 Live)..." else "Connected • Real-time duplex voice"
                                GeminiLiveClient.LiveConnectionState.CONNECTING -> "Handshaking WebSocket..."
                                GeminiLiveClient.LiveConnectionState.ERROR -> "Offline / Check API key"
                                GeminiLiveClient.LiveConnectionState.DISCONNECTED -> "Tap 'Start Live' for real-time talk"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = when (liveState) {
                                GeminiLiveClient.LiveConnectionState.CONNECTED -> CyberEmerald
                                GeminiLiveClient.LiveConnectionState.CONNECTING -> CyberAmber
                                GeminiLiveClient.LiveConnectionState.ERROR -> CyberRose
                                GeminiLiveClient.LiveConnectionState.DISCONNECTED -> TextSecondary
                            }
                        )
                    }
                }

                // Action Button
                if (isConnected || isConnecting) {
                    IconButton(
                        onClick = { onEndSession() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberRose.copy(alpha = 0.2f))
                            .testTag("end_live_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Live Call",
                            tint = CyberRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = { onStartSession() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF0A0F1D)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("start_live_session_button")
                    ) {
                        Text(
                            text = "Start Live",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Live Transcript Display
            AnimatedVisibility(visible = isConnected && liveTranscript.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = liveTranscript,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = CyberCyan
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chip display for Search Grounding Sources (Google Search tool)
 */
@Composable
fun SearchGroundingSourcesRow(
    sources: List<SearchSourceItem>,
    queries: List<String>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty() && queries.isEmpty()) return

    Column(modifier = modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Google Search Grounding",
                tint = CyberAmber,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Grounded by Google Search (gemini-3.5-flash)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = CyberAmber
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            sources.take(3).forEach { source ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberCard)
                        .border(1.dp, CyberAmber.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { onOpenUrl(source.url) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = source.title.take(24) + if (source.title.length > 24) "..." else "",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CyberAmber
                    )
                }
            }
        }
    }
}
