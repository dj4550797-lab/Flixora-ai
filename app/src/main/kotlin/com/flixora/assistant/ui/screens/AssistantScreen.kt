package com.flixora.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*

@Composable
fun AssistantScreen() {
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    
    // Simulate state changes for demo (in reality, this would be reactive to WebSocket events)
    LaunchedEffect(Unit) {
        // Just ambient demo
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)), // Deep dark futuristic background
        contentAlignment = Alignment.Center
    ) {
        // Flixora Orb
        FlixoraOrb(state = state)

        // Status Text
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FLIXORA",
                color = Color(0xFF60A5FA),
                fontSize = 12.sp,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp
            )
        }
    }
}

enum class AssistantState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    THINKING("Processing..."),
    SPEAKING("Speaking...")
}

@Composable
fun FlixoraOrb(state: AssistantState) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer Glow
        Canvas(modifier = Modifier.size(300.dp)) {
            val color = when (state) {
                AssistantState.IDLE -> Color(0xFF1E40AF).copy(alpha = 0.3f)
                AssistantState.LISTENING -> Color(0xFF2563EB).copy(alpha = 0.5f)
                AssistantState.THINKING -> Color(0xFF7C3AED).copy(alpha = 0.6f)
                AssistantState.SPEAKING -> Color(0xFF3B82F6).copy(alpha = 0.7f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2 * scale
                )
            )
        }

        // Animated Ring
        Canvas(modifier = Modifier.size(240.dp)) {
            val color = Color(0xFF60A5FA)
            drawArc(
                color = color,
                startAngle = rotation,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color.copy(alpha = 0.3f),
                startAngle = rotation + 180f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Central Orb
        Surface(
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            color = Color(0xFF1D4ED8),
            shadowElevation = 20.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation * 0.5f
                    }
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                        )
                    )
            )
        }
    }
}
