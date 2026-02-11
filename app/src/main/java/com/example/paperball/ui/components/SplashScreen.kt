package com.example.paperball.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    var showContent by remember { mutableStateOf(false) }
    
    // Animated ball position
    val infiniteTransition = rememberInfiniteTransition(label = "ball")
    val ballY by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ballY"
    )
    
    val ballRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Fade in animation
    val alpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(800),
        label = "alpha"
    )
    
    // Scale animation for icon
    val iconScale by animateFloatAsState(
        targetValue = if (showContent) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )
    
    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4E04D))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Horizontal notepad lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineSpacing = 60f
            val lineCount = (size.height / lineSpacing).toInt()
            repeat(lineCount) { i ->
                val y = i * lineSpacing
                drawLine(
                    color = Color(0xFFD4C03D).copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App Icon - Large Paper Ball
            Canvas(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = size.minDimension / 2.5f
                
                // Outer glow
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = radius * 1.3f,
                    center = Offset(centerX, centerY)
                )
                
                // Shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = radius * 1.05f,
                    center = Offset(centerX + 8f, centerY + 8f)
                )
                
                // Main ball
                drawCircle(
                    color = Color(0xFFF5F5F5),
                    radius = radius,
                    center = Offset(centerX, centerY)
                )
                
                // 3D shading
                drawCircle(
                    color = Color(0xFFE0E0E0),
                    radius = radius * 0.7f,
                    center = Offset(centerX + radius * 0.2f, centerY + radius * 0.2f)
                )
                
                // Crumpled texture - more detailed
                val creaseAngles = listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f)
                creaseAngles.forEach { angle ->
                    val rad = Math.toRadians(angle.toDouble())
                    val startDist = radius * 0.2f
                    val endDist = radius * 0.95f
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.35f),
                        start = Offset(
                            centerX + cos(rad).toFloat() * startDist,
                            centerY + sin(rad).toFloat() * startDist
                        ),
                        end = Offset(
                            centerX + cos(rad).toFloat() * endDist,
                            centerY + sin(rad).toFloat() * endDist
                        ),
                        strokeWidth = 2.5f
                    )
                }
                
                // Wrinkle circles
                repeat(8) { i ->
                    val angle = i * 45f
                    val rad = Math.toRadians(angle.toDouble())
                    val dist = radius * 0.5f
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.2f),
                        radius = radius * 0.15f,
                        center = Offset(
                            centerX + cos(rad).toFloat() * dist,
                            centerY + sin(rad).toFloat() * dist
                        )
                    )
                }
                
                // Outline
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.7f),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 4f)
                )
                
                // Highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = radius * 0.35f,
                    center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Title with fade in
            Text(
                "Paper Ball",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Toss",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF6B5D3F).copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Pulsing "Tap to Start"
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.White.copy(alpha = alpha * 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Tap to Start",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B5D3F).copy(alpha = pulseAlpha),
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 18.dp)
                )
            }
        }
        
        // Bottom info
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🎯 Flick the ball into the cup",
                fontSize = 15.sp,
                color = Color(0xFF6B5D3F).copy(alpha = alpha * 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "📍 Drag the cup to reposition",
                fontSize = 15.sp,
                color = Color(0xFF6B5D3F).copy(alpha = alpha * 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
