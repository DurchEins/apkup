package com.apkup.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkup.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class ConfettiSpec(
    val relX: Float,
    val initialRelY: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val waveOffset: Float,
    val rotationSpeed: Float
)

private val CelebrationPalette = listOf(
    Color(0xFFF59E0B), // Warm Amber
    Color(0xFFFBBF24), // Golden Amber
    Color(0xFFD97706), // Deep Amber
    Color(0xFFFEF3C7), // Light Gold Spark
    Color(0xFFF97316), // Warm Orange Spark
    Color(0xFFEAB308)  // Bright Gold Spark
)

@Composable
fun GrandFestivalEmptyState(
    title: String = stringResource(R.string.updates_empty_title),
    message: String = stringResource(R.string.updates_empty_message),
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "grand_festival_particles")

    // Continuous loop progression 0f..1f (period 4500ms)
    val loopProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loopProgress"
    )

    // Secondary burst pulse 0f..1f (period 2200ms)
    val burstPulse = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "burstPulse"
    )

    // Pre-calculated confetti particles for consistent 60fps rendering
    val confettiList = remember {
        List(42) { index ->
            ConfettiSpec(
                relX = ((index * 37) % 100) / 100f,
                initialRelY = ((index * 53) % 100) / 100f,
                speed = 0.7f + ((index % 5) * 0.15f),
                size = 7f + ((index % 4) * 3f),
                color = CelebrationPalette[index % CelebrationPalette.size],
                waveOffset = (index * 0.8f),
                rotationSpeed = if (index % 2 == 0) 360f else -360f
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Edge-to-Edge Ambient Particle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = loopProgress.value
            val pulse = burstPulse.value

            // Fireworks explosions in background
            drawFireworkBurst(
                center = Offset(widthPx * 0.22f, heightPx * 0.22f),
                pulse = (pulse + 0.15f) % 1f,
                palette = CelebrationPalette
            )
            drawFireworkBurst(
                center = Offset(widthPx * 0.78f, heightPx * 0.18f),
                pulse = (pulse + 0.65f) % 1f,
                palette = CelebrationPalette
            )
            drawFireworkBurst(
                center = Offset(widthPx * 0.50f, heightPx * 0.32f),
                pulse = pulse,
                palette = CelebrationPalette
            )

            // Confetti rain fluttering down
            confettiList.forEach { conf ->
                val currentY = ((conf.initialRelY + progress * conf.speed) % 1.15f) * heightPx - (heightPx * 0.05f)
                val waveX = sin((progress * 4f * PI.toFloat()) + conf.waveOffset) * 24f
                val currentX = (conf.relX * widthPx + waveX).coerceIn(0f, widthPx)
                val rotation = (progress * conf.rotationSpeed) % 360f

                rotate(rotation, pivot = Offset(currentX, currentY)) {
                    drawRoundRect(
                        color = conf.color.copy(alpha = 0.82f),
                        topLeft = Offset(currentX - conf.size / 2f, currentY - conf.size / 4f),
                        size = Size(conf.size, conf.size * 0.55f),
                        cornerRadius = CornerRadius(2.5f, 2.5f)
                    )
                }
            }
        }

        // 2. Centered Frosted Readability Glass Plate
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Joyful Cheering Bugdroid Mascot
                JubilantBugdroidIllustration(
                    modifier = Modifier.size(110.dp)
                )

                // Headline
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Subtitle
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(4.dp))

                // Search for updates action button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefresh()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.refresh_updates),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawFireworkBurst(
    center: Offset,
    pulse: Float,
    palette: List<Color>
) {
    if (pulse < 0.05f || pulse > 0.95f) return

    val maxRadius = 110f
    val currentRadius = pulse * maxRadius
    val alpha = ((1f - pulse) * 1.2f).coerceIn(0f, 0.75f)

    // Expanding shockwave ring
    drawCircle(
        color = palette[0].copy(alpha = alpha * 0.4f),
        radius = currentRadius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Spark particles radiating outward
    val sparkCount = 12
    for (i in 0 until sparkCount) {
        val angle = (i * (2 * PI / sparkCount)).toFloat()
        val sparkDist = currentRadius * (0.85f + (i % 3) * 0.1f)
        val sparkPos = Offset(
            x = center.x + cos(angle) * sparkDist,
            y = center.y + sin(angle) * sparkDist
        )
        val sparkColor = palette[i % palette.size].copy(alpha = alpha)

        drawCircle(
            color = sparkColor,
            radius = 3.5f * (1f - pulse * 0.5f),
            center = sparkPos
        )
    }
}

@Composable
private fun JubilantBugdroidIllustration(
    modifier: Modifier = Modifier
) {
    val androidGreen = Color(0xFF3DDC84)
    val eyeColor = Color.White

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Central Bugdroid Head & Body Geometry
        val headRadius = w * 0.28f
        val headCenter = Offset(w * 0.5f, h * 0.48f)

        // Dome / Head
        val headPath = Path().apply {
            moveTo(headCenter.x - headRadius, headCenter.y)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    headCenter.x - headRadius,
                    headCenter.y - headRadius,
                    headCenter.x + headRadius,
                    headCenter.y + headRadius
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(headPath, color = androidGreen)

        // Left Antenna (Joyfully angled outward)
        drawLine(
            color = androidGreen,
            start = Offset(headCenter.x - headRadius * 0.5f, headCenter.y - headRadius * 0.82f),
            end = Offset(headCenter.x - headRadius * 0.85f, headCenter.y - headRadius * 1.35f),
            strokeWidth = 6f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Right Antenna (Joyfully angled outward)
        drawLine(
            color = androidGreen,
            start = Offset(headCenter.x + headRadius * 0.5f, headCenter.y - headRadius * 0.82f),
            end = Offset(headCenter.x + headRadius * 0.85f, headCenter.y - headRadius * 1.35f),
            strokeWidth = 6f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Playful Happy Eyes
        val eyeRadius = 5.5f
        val eyeOffsetY = headCenter.y - headRadius * 0.45f
        drawCircle(
            color = eyeColor,
            radius = eyeRadius,
            center = Offset(headCenter.x - headRadius * 0.42f, eyeOffsetY)
        )
        drawCircle(
            color = eyeColor,
            radius = eyeRadius,
            center = Offset(headCenter.x + headRadius * 0.42f, eyeOffsetY)
        )

        // Raised Cheering Arms (Cheering to the sky)
        // Left arm angled up
        drawLine(
            color = androidGreen,
            start = Offset(headCenter.x - headRadius * 1.05f, headCenter.y + 12f),
            end = Offset(headCenter.x - headRadius * 1.45f, headCenter.y - headRadius * 0.5f),
            strokeWidth = 10f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Right arm angled up
        drawLine(
            color = androidGreen,
            start = Offset(headCenter.x + headRadius * 1.05f, headCenter.y + 12f),
            end = Offset(headCenter.x + headRadius * 1.45f, headCenter.y - headRadius * 0.5f),
            strokeWidth = 10f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Body Pill Segment
        drawRoundRect(
            color = androidGreen,
            topLeft = Offset(headCenter.x - headRadius * 0.95f, headCenter.y + 8f),
            size = Size(headRadius * 1.9f, h * 0.28f),
            cornerRadius = CornerRadius(14f, 14f)
        )

        // Party Stars around Bugdroid
        drawCircle(
            color = Color(0xFFFBBC05),
            radius = 4f,
            center = Offset(w * 0.18f, h * 0.25f)
        )
        drawCircle(
            color = Color(0xFF4285F4),
            radius = 3.5f,
            center = Offset(w * 0.82f, h * 0.22f)
        )
        drawCircle(
            color = Color(0xFFEA4335),
            radius = 3f,
            center = Offset(w * 0.88f, h * 0.55f)
        )
    }
}
