package com.example.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Attractive, polished Stopwatch / Chronometer Splash Screen Animation.
 * Features an energetic sweeping second hand, ticking gear pulse, glowing neon trail,
 * and a smooth transition to the main app screen.
 */
@Composable
fun AnimatedTimerSplash(
  onSplashFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isVisible by remember { mutableStateOf(true) }

  // Scale and Alpha entry animations
  val scaleAnim = remember { Animatable(0.7f) }
  val alphaAnim = remember { Animatable(0f) }

  // Stopwatch hand high-speed spin animation
  val infiniteTransition = rememberInfiniteTransition(label = "StopwatchTransition")
  val rotationDegrees by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "HandRotation"
  )

  // Subtle pulsing glow
  val pulseGlow by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "PulseGlow"
  )

  LaunchedEffect(Unit) {
    // Initial reveal
    scaleAnim.animateTo(
      targetValue = 1.0f,
      animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
    )
    alphaAnim.animateTo(
      targetValue = 1.0f,
      animationSpec = tween(durationMillis = 400)
    )

    // Keep splash active for an enjoyable, attractive 1.8s
    delay(1800)

    // Smooth exit
    isVisible = false
    delay(400)
    onSplashFinished()
  }

  AnimatedVisibility(
    visible = isVisible,
    exit = fadeOut(animationSpec = tween(durationMillis = 350))
  ) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF0F172A), // Deep rich midnight slate
              Color(0xFF090D16),
              Color(0xFF020408)
            )
          )
        )
        .testTag("animated_splash_screen"),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Glowing Stopwatch Animated Canvas
        Box(
          modifier = Modifier
            .size(190.dp)
            .testTag("stopwatch_animation_box"),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            drawStopwatch(
              rotationDegrees = rotationDegrees,
              pulseGlow = pulseGlow
            )
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // App Title / Branding in display typography
        Text(
          text = "مؤقت رد الفعل",
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "دقة متناهية وسرعة استجابة",
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF94A3B8),
          letterSpacing = 0.5.sp
        )
      }
    }
  }
}

/**
 * Draws a gorgeous, high-precision chronograph stopwatch with:
 * - Crown & side pushers
 * - Dual-tone metallic bezel
 * - Precise tick marks (seconds & sub-seconds)
 * - Sweeping neon-red chronograph needle with glow counterweight
 * - Center pivot mechanical cap
 */
private fun DrawScope.drawStopwatch(
  rotationDegrees: Float,
  pulseGlow: Float
) {
  val centerX = size.width / 2f
  val centerY = size.height / 2f + 8f
  val radius = (size.minDimension / 2f) - 22f

  // 1. Stopwatch Top Crown & Push Buttons
  val crownWidth = 24f
  val crownHeight = 16f
  drawRoundRect(
    color = Color(0xFF64748B),
    topLeft = Offset(centerX - crownWidth / 2f, centerY - radius - crownHeight - 10f),
    size = Size(crownWidth, crownHeight),
    cornerRadius = CornerRadius(4f, 4f)
  )

  // Top Loop ring
  drawCircle(
    color = Color(0xFF475569),
    radius = 18f,
    center = Offset(centerX, centerY - radius - 16f),
    style = Stroke(width = 4f)
  )

  // 45-degree Right Pusher
  rotate(degrees = 40f, pivot = Offset(centerX, centerY)) {
    drawRoundRect(
      color = Color(0xFF22C55E), // Green accent start pusher
      topLeft = Offset(centerX - 10f, centerY - radius - 14f),
      size = Size(20f, 12f),
      cornerRadius = CornerRadius(3f, 3f)
    )
  }

  // -45-degree Left Pusher
  rotate(degrees = -40f, pivot = Offset(centerX, centerY)) {
    drawRoundRect(
      color = Color(0xFFEF4444), // Red accent reset pusher
      topLeft = Offset(centerX - 10f, centerY - radius - 14f),
      size = Size(20f, 12f),
      cornerRadius = CornerRadius(3f, 3f)
    )
  }

  // 2. Outer Ambient Glow Halo
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        Color(0x3338BDF8),
        Color(0x0038BDF8)
      ),
      center = Offset(centerX, centerY),
      radius = radius * 1.35f * pulseGlow
    ),
    radius = radius * 1.35f * pulseGlow,
    center = Offset(centerX, centerY)
  )

  // 3. Stopwatch Main Outer Metallic Body Case
  drawCircle(
    brush = Brush.linearGradient(
      colors = listOf(
        Color(0xFF334155),
        Color(0xFF1E293B),
        Color(0xFF0F172A)
      )
    ),
    radius = radius,
    center = Offset(centerX, centerY)
  )

  // Bezel Ring
  drawCircle(
    color = Color(0xFF0284C7).copy(alpha = 0.4f),
    radius = radius - 3f,
    center = Offset(centerX, centerY),
    style = Stroke(width = 2.5f)
  )

  // Inner Dial Face
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        Color(0xFF0B1120),
        Color(0xFF030712)
      ),
      center = Offset(centerX, centerY),
      radius = radius - 6f
    ),
    radius = radius - 6f,
    center = Offset(centerX, centerY)
  )

  // 4. Dial Tick Marks (60 ticks: major every 5s)
  val tickRadius = radius - 10f
  for (i in 0 until 60) {
    val angleRad = Math.toRadians((i * 6).toDouble() - 90)
    val isMajor = (i % 5 == 0)
    val tickLength = if (isMajor) 10f else 5f
    val strokeW = if (isMajor) 2.5f else 1.2f
    val tickColor = if (isMajor) Color(0xFF38BDF8) else Color(0xFF475569)

    val startX = centerX + (tickRadius - tickLength) * cos(angleRad).toFloat()
    val startY = centerY + (tickRadius - tickLength) * sin(angleRad).toFloat()
    val endX = centerX + tickRadius * cos(angleRad).toFloat()
    val endY = centerY + tickRadius * sin(angleRad).toFloat()

    drawLine(
      color = tickColor,
      start = Offset(startX, startY),
      end = Offset(endX, endY),
      strokeWidth = strokeW,
      cap = StrokeCap.Round
    )
  }

  // 5. Sweeping Chronograph Needle (Neon Red with dynamic trail glow)
  rotate(degrees = rotationDegrees, pivot = Offset(centerX, centerY)) {
    // Dynamic Sweeping Speed Blur / Trail
    val trailPath = Path().apply {
      moveTo(centerX, centerY)
      val trailAngle = -35.0
      val trailRad = Math.toRadians(trailAngle - 90.0)
      val trailX = centerX + (radius * 0.72f) * cos(trailRad).toFloat()
      val trailY = centerY + (radius * 0.72f) * sin(trailRad).toFloat()
      lineTo(trailX, trailY)
      lineTo(centerX, centerY - (radius * 0.82f))
      close()
    }
    drawPath(
      path = trailPath,
      brush = Brush.linearGradient(
        colors = listOf(
          Color(0x00FF2A32),
          Color(0x44FF2A32)
        )
      )
    )

    // Needle Back Tail
    drawLine(
      color = Color(0xFF991B1B),
      start = Offset(centerX, centerY),
      end = Offset(centerX, centerY + 18f),
      strokeWidth = 3f,
      cap = StrokeCap.Round
    )

    // Main Needle Body
    drawLine(
      brush = Brush.verticalGradient(
        colors = listOf(
          Color(0xFFFF3B30),
          Color(0xFFFF5252),
          Color(0xFFFF1744)
        )
      ),
      start = Offset(centerX, centerY),
      end = Offset(centerX, centerY - (radius * 0.82f)),
      strokeWidth = 3.5f,
      cap = StrokeCap.Round
    )

    // Sharp Pointer Tip Arrow
    val tipY = centerY - (radius * 0.84f)
    drawCircle(
      color = Color(0xFFFF2A32),
      radius = 3.5f,
      center = Offset(centerX, tipY)
    )
  }

  // 6. Mechanical Center Cap Pin
  drawCircle(
    color = Color(0xFF0F172A),
    radius = 9f,
    center = Offset(centerX, centerY)
  )
  drawCircle(
    color = Color(0xFFE2E8F0),
    radius = 5.5f,
    center = Offset(centerX, centerY)
  )
  drawCircle(
    color = Color(0xFFFF2A32),
    radius = 2.5f,
    center = Offset(centerX, centerY)
  )
}
