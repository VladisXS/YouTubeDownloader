package com.vladisx.youtubedownloader.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/* ------------------------------------------------------------------ */
/*  Background                                                         */
/* ------------------------------------------------------------------ */

/** Deep-space vertical gradient: violet horizon up top, void at the bottom. */
fun cosmicBackgroundBrush(): Brush = Brush.verticalGradient(
    0.0f to Color(0xFF100A2E),
    0.28f to Color(0xFF070418),
    0.62f to Color(0xFF04030C),
    1.0f to Color(0xFF0B0524),
)

/**
 * Full-screen backdrop: gradient + twinkling star field + nebula glows.
 * Purely decorative, swallows no touches.
 */
@Composable
fun CosmicBackdrop(modifier: Modifier = Modifier) {
    val brush = cosmicBackgroundBrush()

    val stars = remember {
        val rnd = Random(2024)
        List(110) {
            Star(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                radius = rnd.nextFloat() * 1.5f + 0.5f,
                phase = rnd.nextFloat(),
                speed = 1400 + rnd.nextInt(2600),
            )
        }
    }

    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "twinkle",
    )

    val drift by rememberInfiniteTransition(label = "nebula").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Nebula glows
            val nebulaAlpha = 0.26f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Nebula.copy(alpha = nebulaAlpha), Color.Transparent),
                ),
                radius = w * 0.75f,
                center = Offset(w * (0.15f + 0.10f * drift), h * 0.06f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Ion.copy(alpha = nebulaAlpha * 0.8f), Color.Transparent),
                ),
                radius = w * 0.65f,
                center = Offset(w * (0.95f - 0.12f * drift), h * 0.42f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Solar.copy(alpha = nebulaAlpha * 0.55f), Color.Transparent),
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.2f, h * 0.95f),
            )

            // Star field
            for (star in stars) {
                val pulsate = 0.35f + 0.65f * ((twinkle + star.phase) % 1f)
                val alpha = 0.15f + 0.85f * pulsate
                val color = when ((star.phase * 10).toInt() % 3) {
                    0 -> Color.White
                    1 -> Ion
                    else -> Nebula
                }
                drawCircle(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = star.radius,
                    center = Offset(star.x * w, star.y * h),
                )
            }
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float,
    val speed: Int,
)

/* ------------------------------------------------------------------ */
/*  Surfaces                                                           */
/* ------------------------------------------------------------------ */

/** Gradient border used on hero/primary surfaces. */
@Composable
fun cosmicBorderBrush(): Brush = Brush.linearGradient(
    listOf(
        Nebula.copy(alpha = 0.75f),
        Ion.copy(alpha = 0.55f),
        Solar.copy(alpha = 0.35f),
    )
)

/**
 * Glassy card with a soft gradient edge and a faint nebula wash inside.
 */
@Composable
fun CosmicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    glow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val borderBrush = if (glow) cosmicBorderBrush() else Brush.linearGradient(
        listOf(scheme.outline.copy(alpha = 0.55f), scheme.outlineVariant.copy(alpha = 0.35f)),
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surface.copy(alpha = 0.92f),
                        scheme.surfaceVariant.copy(alpha = 0.72f),
                    )
                )
            )
            .border(1.dp, borderBrush, shape),
        content = content,
    )
}

/** Reusable solid-to-gradient fill for primary actions. */
@Composable
fun cosmicAccentBrush(): Brush = Brush.linearGradient(
    listOf(NebulaDeep, Nebula, Ion),
)

@Composable
fun cosmicSelectedBrush(): Brush = Brush.linearGradient(
    listOf(Nebula.copy(alpha = 0.30f), Ion.copy(alpha = 0.18f)),
)

