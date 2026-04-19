package com.gemmaremember.app.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*
import kotlin.random.Random

private data class Particle(
    val baseAngle: Float,
    val baseRadius: Float,
    val size: Float,
    val speed: Float,
    val phaseOffset: Float
)

@Composable
fun GemmaStarVisualizer(
    isSpeaking: Boolean,
    isListening: Boolean,
    audioAmplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    val isActive = isSpeaking || isListening

    // Animation time
    val infiniteTransition = rememberInfiniteTransition(label = "star")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Smooth the audio amplitude
    val smoothAmplitude by animateFloatAsState(
        targetValue = audioAmplitude,
        animationSpec = tween(100),
        label = "amplitude"
    )

    // Pulse combines active state + audio amplitude
    val pulseTarget = if (isActive) (0.6f + smoothAmplitude * 0.4f) else 0.3f
    val pulse by animateFloatAsState(
        targetValue = pulseTarget,
        animationSpec = tween(150),
        label = "pulse"
    )

    // Glow intensity
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Generate particles once
    val particles = remember { generateStarParticles(300) }

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val maxRadius = size.minDimension / 2 * 0.85f

        // Draw each particle
        particles.forEach { particle ->
            drawStarParticle(
                particle = particle,
                cx = cx,
                cy = cy,
                maxRadius = maxRadius,
                time = time,
                pulse = pulse,
                glow = glow,
                isSpeaking = isSpeaking
            )
        }

        // Central glow
        val glowRadius = maxRadius * 0.15f * (0.8f + pulse * 0.4f)
        for (i in 3 downTo 0) {
            val r = glowRadius * (1 + i * 0.5f)
            val alpha = (0.15f - i * 0.03f) * glow * (0.5f + pulse * 0.5f)
            drawCircle(
                color = Color(0xFFFFF3E0).copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = r,
                center = Offset(cx, cy)
            )
        }

        // Bright center
        drawCircle(
            color = Color.White.copy(alpha = 0.6f * glow * (0.5f + pulse * 0.5f)),
            radius = glowRadius * 0.3f,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawStarParticle(
    particle: Particle,
    cx: Float,
    cy: Float,
    maxRadius: Float,
    time: Float,
    pulse: Float,
    glow: Float,
    isSpeaking: Boolean
) {
    val angle = particle.baseAngle
    val baseR = particle.baseRadius * maxRadius

    // Star shape: 4-point star using cos(2*theta) envelope
    // This creates the distinctive Gemma diamond/star shape
    val starEnvelope = getStarRadius(angle)
    val r = baseR * starEnvelope

    // Add animation — particles breathe in and out
    val breathe = sin(time * particle.speed + particle.phaseOffset)
    val animatedR = r * (1f + breathe * 0.06f * pulse)

    // Speaking: particles jitter more
    val jitter = if (isSpeaking) {
        sin(time * 8f + particle.phaseOffset * 3f) * maxRadius * 0.02f * pulse
    } else 0f

    val finalR = animatedR + jitter

    val x = cx + cos(angle) * finalR
    val y = cy + sin(angle) * finalR

    // Color: warm white to gold gradient based on position
    val warmth = (starEnvelope * 0.5f + 0.5f)
    val color = lerpColor(
        Color(0xFFE8D5B7),  // warm gold
        Color(0xFFFFFFFF),  // white
        warmth * glow
    )

    // Size varies with pulse
    val particleSize = particle.size * (0.8f + pulse * 0.4f)

    // Alpha: particles near center are brighter
    val distFromCenter = finalR / maxRadius
    val alpha = ((1f - distFromCenter * 0.5f) * (0.4f + glow * 0.6f) * (0.3f + pulse * 0.7f))
        .coerceIn(0f, 1f)

    drawCircle(
        color = color.copy(alpha = alpha),
        radius = particleSize,
        center = Offset(x, y)
    )
}

// 4-point star shape function
private fun getStarRadius(angle: Float): Float {
    // Creates a 4-point star: peaks at 0, PI/2, PI, 3PI/2
    val cos2 = cos(2 * angle)
    return (0.4f + 0.6f * abs(cos2).pow(0.8f))
}

private fun generateStarParticles(count: Int): List<Particle> {
    val random = Random(42) // deterministic for consistency
    return List(count) {
        val angle = random.nextFloat() * 2 * PI.toFloat()
        Particle(
            baseAngle = angle,
            baseRadius = 0.1f + random.nextFloat() * 0.9f,
            size = 1.5f + random.nextFloat() * 3f,
            speed = 0.5f + random.nextFloat() * 1.5f,
            phaseOffset = random.nextFloat() * 2 * PI.toFloat()
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val ct = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * ct,
        green = a.green + (b.green - a.green) * ct,
        blue = a.blue + (b.blue - a.blue) * ct,
        alpha = a.alpha + (b.alpha - a.alpha) * ct
    )
}

private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
