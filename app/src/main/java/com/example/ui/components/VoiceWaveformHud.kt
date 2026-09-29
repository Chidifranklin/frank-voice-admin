package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import kotlin.math.sin

@Composable
fun VoiceWaveformHud(
    isListening: Boolean,
    isSpeaking: Boolean,
    isPlayingMusic: Boolean,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (isListening || isSpeaking || isPlayingMusic) 1.12f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 4000 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val statusColor = when {
        isSpeaking -> CyberCyan
        isListening -> CyberEmerald
        isPlayingMusic -> CyberAmber
        else -> CyberEmerald
    }

    val statusText = when {
        isSpeaking -> "SYNTHESIZING ON-DEVICE SPEECH"
        isListening -> "CONSTANTLY LISTENING • NO TAPPING NEEDED"
        isPlayingMusic -> "PLAYING AMBIENT FOCUS AUDIO"
        else -> "ACTIVE • SPEAK ANY COMMAND FREELY"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Holographic Glowing Orb
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(130.dp)
                .scale(pulseScale)
        ) {
            // Concentric Canvas Rings
            Canvas(modifier = Modifier.size(130.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 6.dp.toPx()

                // Outer ambient glow ring
                drawCircle(
                    color = statusColor.copy(alpha = 0.15f),
                    radius = radius + 6.dp.toPx()
                )

                // Rotating Dashed Ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.9f),
                            Color.Transparent,
                            statusColor.copy(alpha = 0.4f),
                            statusColor.copy(alpha = 0.8f)
                        ),
                        center = center
                    ),
                    radius = radius,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Inner core halo
                drawCircle(
                    color = statusColor.copy(alpha = 0.22f),
                    radius = radius * 0.65f
                )
            }

            // Core Interactive Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                statusColor.copy(alpha = 0.4f),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(2.dp, statusColor, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 38.dp),
                        onClick = onOrbClick
                    )
                    .testTag("voice_orb_button")
            ) {
                Icon(
                    imageVector = when {
                        isSpeaking -> Icons.AutoMirrored.Filled.VolumeUp
                        isListening -> Icons.Default.Mic
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Voice Assistant Orb",
                    tint = statusColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Label with glowing cyber badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = statusColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Frequency Waveform Visualizer
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(38.dp)
        ) {
            val barCount = 28
            val totalWidth = size.width
            val barWidth = (totalWidth / barCount) * 0.55f
            val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1)
            val centerY = size.height / 2f

            for (i in 0 until barCount) {
                val x = i * (barWidth + spacing)
                // Generate sinusoidal dynamic amplitude
                val normalizedIndex = i.toFloat() / barCount
                val sineMod = sin(normalizedIndex * Math.PI * 3 + wavePhase).toFloat()
                val activeMultiplier = if (isListening || isSpeaking || isPlayingMusic) 1.0f else 0.25f

                val barHeight = ((size.height * 0.2f) + (size.height * 0.75f * Math.abs(sineMod) * activeMultiplier))
                    .coerceIn(4.dp.toPx(), size.height)

                val barColor = if (i % 2 == 0) statusColor else CyberCyan

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.3f)),
                        startY = centerY - barHeight / 2f,
                        endY = centerY + barHeight / 2f
                    ),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
