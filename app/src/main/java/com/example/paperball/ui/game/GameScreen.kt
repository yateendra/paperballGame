package com.example.paperball.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.paperball.models.Ball
import com.example.paperball.models.Bin
import com.example.paperball.models.GameState
import com.example.paperball.models.Particle
import com.example.paperball.models.WindState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

@Composable
fun PaperBallGame() {
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    // Convert dp to px ONCE at composable level (not in models)
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val ballRadiusPx = with(density) { 24.dp.toPx() }

    var gameState by remember {
        mutableStateOf(GameState(
            ball = Ball(position = Offset(screenWidthPx * 0.2f, screenHeightPx * 0.8f)),
            bin = Bin(position = Offset(screenWidthPx * 0.75f, screenHeightPx * 0.7f)),
            wind = WindState(
                direction = if (Random.nextBoolean()) -1f else 1f,
                strength = Random.nextFloat() * 300f + 100f,
                fanPosition = Offset(
                    if (Random.nextBoolean()) screenWidthPx * 0.1f else screenWidthPx * 0.9f,
                    screenHeightPx * 0.3f
                ),
                isActive = true
            )
        ))
    }

    // Bin position - moves based on score for difficulty
    val binX = remember(gameState.score) {
        when {
            gameState.score < 3 -> screenWidthPx * 0.75f
            gameState.score < 6 -> screenWidthPx * 0.65f
            gameState.score < 10 -> screenWidthPx * 0.55f
            else -> screenWidthPx * (0.4f + (gameState.score % 3) * 0.15f)
        }
    }
    val binY = screenHeightPx * 0.7f

    val scope = rememberCoroutineScope()

    // Particle animation
    LaunchedEffect(gameState.particles) {
        if (gameState.particles.isNotEmpty()) {
            while (gameState.particles.isNotEmpty()) {
                delay(16)
                gameState = gameState.copy(
                    particles = gameState.particles.mapNotNull { particle ->
                        val newAlpha = particle.alpha - 0.02f
                        if (newAlpha <= 0f) null
                        else particle.copy(
                            position = Offset(
                                particle.position.x + particle.velocity.x,
                                particle.position.y + particle.velocity.y
                            ),
                            velocity = Offset(
                                particle.velocity.x * 0.98f,
                                particle.velocity.y + 0.5f
                            ),
                            alpha = newAlpha
                        )
                    }
                )
            }
        }
    }

    // Physics simulation
    LaunchedEffect(gameState.isAnimating) {
        if (gameState.isAnimating && !gameState.ball.inHand) {
            var vx = gameState.ball.velocity.x
            var vy = gameState.ball.velocity.y
            var x = gameState.ball.position.x
            var y = gameState.ball.position.y
            var rotation = gameState.ball.rotation
            var scale = 1f
            val gravity = 2500f
            val drag = 0.99f
            val bounceDamping = 0.6f
            
            // Wind force
            val windForce = gameState.wind.strength * gameState.wind.direction

            while (y < screenHeightPx + 100) {
                vy += gravity * 0.016f
                vx *= drag
                
                // Apply wind force (stronger when ball is higher)
                val windEffect = windForce * (1f - (y / screenHeightPx).coerceIn(0f, 1f)) * 0.016f
                vx += windEffect
                
                x += vx * 0.016f
                y += vy * 0.016f
                
                // Rotation based on velocity (3D tumbling effect)
                rotation += (vx * 0.5f + vy * 0.3f) * 0.016f
                
                // Keep scale constant - no zoom effect
                scale = 1f

                // Top boundary - bounce back if going too high
                if (y < ballRadiusPx && vy < 0) {
                    vy = -vy * bounceDamping
                    y = ballRadiusPx
                }

                // Wall bounces - keep ball on screen
                if (x < ballRadiusPx) {
                    vx = -vx * bounceDamping
                    x = ballRadiusPx
                } else if (x > screenWidthPx - ballRadiusPx) {
                    vx = -vx * bounceDamping
                    x = screenWidthPx - ballRadiusPx
                }

                // Floor bounce
                if (y > screenHeightPx * 0.85f - ballRadiusPx && vy > 0) {
                    if (abs(vy) > 100f) {
                        vy = -vy * bounceDamping
                        y = screenHeightPx * 0.85f - ballRadiusPx
                        vx *= 0.9f // Extra friction on floor
                    } else {
                        // Ball stopped bouncing on floor - check if missed
                        gameState = gameState.copy(
                            combo = 0,
                            showMessage = "✗ Missed!"
                        )
                        break
                    }
                }

                // Bin collision check - updated for bigger cup
                val cupWidth = 200f
                val rimHeight = 30f
                val inBin = x in (binX + 20f)..(binX + cupWidth - 20f) &&
                        y in (binY + rimHeight - 10f)..(binY + rimHeight + 35f) &&
                        vy > 0

                if (inBin) {
                    val newCombo = gameState.combo + 1
                    val bonusPoints = if (newCombo > 1) newCombo else 1
                    val newScore = gameState.score + bonusPoints
                    
                    // Create celebration particles
                    val particles = List(20) {
                        Particle(
                            position = Offset(binX + 40f, binY),
                            velocity = Offset(
                                Random.nextFloat() * 10f - 5f,
                                Random.nextFloat() * -15f - 5f
                            ),
                            color = listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFF6B6B),
                                Color(0xFF4ECDC4),
                                Color(0xFF95E1D3)
                            ).random()
                        )
                    }
                    
                    gameState = gameState.copy(
                        score = newScore,
                        highScore = maxOf(newScore, gameState.highScore),
                        combo = newCombo,
                        showMessage = if (newCombo > 1) "🔥 ${bonusPoints}x COMBO!" else "✓ Scored!",
                        particles = particles
                    )
                    break
                }

                // Update ball position
                gameState = gameState.copy(
                    ball = gameState.ball.copy(
                        position = Offset(x, y),
                        velocity = Offset(vx, vy),
                        rotation = rotation,
                        scale = scale
                    )
                )
                delay(16)
            }

            delay(1000)
            // Reset ball with new random wind
            gameState = gameState.copy(
                ball = Ball(position = Offset(screenWidthPx * 0.2f, screenHeightPx * 0.8f)),
                isAnimating = false,
                attempts = gameState.attempts + 1,
                showMessage = null,
                wind = WindState(
                    direction = if (Random.nextBoolean()) -1f else 1f,
                    strength = Random.nextFloat() * 300f + 100f,
                    fanPosition = Offset(
                        if (Random.nextBoolean()) screenWidthPx * 0.1f else screenWidthPx * 0.9f,
                        screenHeightPx * 0.3f
                    ),
                    isActive = true
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4E04D)) // Yellow notepad color
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

        // Checkered floor pattern at bottom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val floorY = size.height * 0.85f
            val checkSize = 30f
            val checksX = (size.width / checkSize).toInt() + 1
            
            repeat(checksX) { i ->
                val isBlack = i % 2 == 0
                drawRect(
                    color = if (isBlack) Color.Black else Color.White,
                    topLeft = Offset(i * checkSize, floorY),
                    size = androidx.compose.ui.geometry.Size(checkSize, checkSize)
                )
            }
        }

        // White Coffee Cup (bigger size)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cupWidth = 360f
            val cupHeight = 420f
            val cupBottom = binY + cupHeight
            
            // Cup shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(binX - 10f, cupBottom - 5f),
                size = androidx.compose.ui.geometry.Size(cupWidth + 20f, 25f)
            )
            
            // Cup body - trapezoid (wider at top)
            val cupPath = Path().apply {
                moveTo(binX + 30f, cupBottom) // Bottom left
                lineTo(binX, binY + 30f) // Top left
                lineTo(binX + cupWidth, binY + 30f) // Top right
                lineTo(binX + cupWidth - 30f, cupBottom) // Bottom right
                close()
            }
            
            // Main cup body - white
            drawPath(
                path = cupPath,
                color = Color(0xFFF5F5F5)
            )
            
            // Left side shading for 3D effect
            val leftShade = Path().apply {
                moveTo(binX + 30f, cupBottom)
                lineTo(binX, binY + 30f)
                lineTo(binX + 20f, binY + 30f)
                lineTo(binX + 40f, cupBottom)
                close()
            }
            drawPath(
                path = leftShade,
                color = Color(0xFFE0E0E0)
            )
            
            // Right side highlight
            val rightHighlight = Path().apply {
                moveTo(binX + cupWidth - 30f, cupBottom)
                lineTo(binX + cupWidth, binY + 30f)
                lineTo(binX + cupWidth - 20f, binY + 30f)
                lineTo(binX + cupWidth - 40f, cupBottom)
                close()
            }
            drawPath(
                path = rightHighlight,
                color = Color.White
            )
            
            // Cup rim - ellipse for perspective
            drawOval(
                color = Color(0xFFE8E8E8),
                topLeft = Offset(binX - 5f, binY + 25f),
                size = androidx.compose.ui.geometry.Size(cupWidth + 10f, 20f)
            )
            
            // Inner rim (darker)
            drawOval(
                color = Color(0xFFD0D0D0),
                topLeft = Offset(binX + 5f, binY + 28f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 10f, 15f)
            )
            
            // Cup opening (dark inside)
            drawOval(
                color = Color(0xFF3D3D3D),
                topLeft = Offset(binX + 15f, binY + 30f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 30f, 12f)
            )
            
            // Rim highlight
            drawOval(
                color = Color.White.copy(alpha = 0.6f),
                topLeft = Offset(binX + 10f, binY + 26f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 20f, 8f)
            )
            
            // Subtle vertical lines for texture
            repeat(3) { i ->
                val xPos = binX + 50f + i * 30f
                drawLine(
                    color = Color(0xFFE0E0E0),
                    start = Offset(xPos, binY + 50f),
                    end = Offset(xPos + 8f, cupBottom - 20f),
                    strokeWidth = 1.5f
                )
            }
        }

        // Trajectory preview and flick indicator when dragging
        if (gameState.isDragging && !gameState.isAnimating) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val power = 8f
                val throwVelocity = Offset(
                    (gameState.dragStart.x - gameState.ball.position.x) * power,
                    (gameState.dragStart.y - gameState.ball.position.y) * power
                )
                
                // Flick arrow indicator
                val arrowLength = 80f
                val arrowAngle = kotlin.math.atan2(
                    throwVelocity.y.toDouble(),
                    throwVelocity.x.toDouble()
                ).toFloat()
                
                val arrowEndX = gameState.ball.position.x + cos(arrowAngle.toDouble()).toFloat() * arrowLength
                val arrowEndY = gameState.ball.position.y + sin(arrowAngle.toDouble()).toFloat() * arrowLength
                
                // Arrow shaft with dashed lines
                repeat(5) { i ->
                    val startRatio = i * 0.2f
                    val endRatio = startRatio + 0.1f
                    drawLine(
                        color = Color(0xFF6B5D3F),
                        start = Offset(
                            gameState.ball.position.x + (arrowEndX - gameState.ball.position.x) * startRatio,
                            gameState.ball.position.y + (arrowEndY - gameState.ball.position.y) * startRatio
                        ),
                        end = Offset(
                            gameState.ball.position.x + (arrowEndX - gameState.ball.position.x) * endRatio,
                            gameState.ball.position.y + (arrowEndY - gameState.ball.position.y) * endRatio
                        ),
                        strokeWidth = 8f
                    )
                }
                
                // Arrow head
                val arrowHeadSize = 20f
                val perpAngle = arrowAngle + Math.PI.toFloat() / 2f
                
                val arrowHeadPath = Path().apply {
                    moveTo(arrowEndX, arrowEndY)
                    lineTo(
                        arrowEndX - cos(arrowAngle.toDouble()).toFloat() * arrowHeadSize + cos(perpAngle.toDouble()).toFloat() * arrowHeadSize / 2f,
                        arrowEndY - sin(arrowAngle.toDouble()).toFloat() * arrowHeadSize + sin(perpAngle.toDouble()).toFloat() * arrowHeadSize / 2f
                    )
                    lineTo(
                        arrowEndX - cos(arrowAngle.toDouble()).toFloat() * arrowHeadSize - cos(perpAngle.toDouble()).toFloat() * arrowHeadSize / 2f,
                        arrowEndY - sin(arrowAngle.toDouble()).toFloat() * arrowHeadSize - sin(perpAngle.toDouble()).toFloat() * arrowHeadSize / 2f
                    )
                    close()
                }
                
                drawPath(
                    path = arrowHeadPath,
                    color = Color(0xFF6B5D3F)
                )
            }
        }

        // Particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            gameState.particles.forEach { particle ->
                drawCircle(
                    color = particle.color.copy(alpha = particle.alpha),
                    radius = particle.size,
                    center = particle.position
                )
            }
        }

        // Paper ball - 3D crumpled effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ball = gameState.ball
            val radiusPx = ballRadiusPx * ball.scale

            // Apply rotation and scale transformations
            rotate(ball.rotation, pivot = ball.position) {
                scale(ball.scale, pivot = ball.position) {
                    // Shadow for depth
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.2f),
                        radius = radiusPx * 1.1f,
                        center = Offset(ball.position.x + 5f, ball.position.y + 5f)
                    )
                    
                    // Main ball with gradient effect (lighter on top-left)
                    drawCircle(
                        color = Color(0xFFF5F5F5),
                        radius = radiusPx,
                        center = ball.position
                    )
                    
                    // Darker bottom-right for 3D effect
                    drawCircle(
                        color = Color(0xFFE0E0E0),
                        radius = radiusPx * 0.7f,
                        center = Offset(ball.position.x + radiusPx * 0.2f, ball.position.y + radiusPx * 0.2f)
                    )

                    // Crumpled paper texture - random creases
                    val creaseAngles = listOf(15f, 45f, 75f, 120f, 160f, 200f, 250f, 300f)
                    creaseAngles.forEach { angle ->
                        val rad = Math.toRadians(angle.toDouble())
                        val startDist = radiusPx * 0.3f
                        val endDist = radiusPx * 0.9f
                        
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.4f),
                            start = Offset(
                                ball.position.x + cos(rad).toFloat() * startDist,
                                ball.position.y + sin(rad).toFloat() * startDist
                            ),
                            end = Offset(
                                ball.position.x + cos(rad).toFloat() * endDist,
                                ball.position.y + sin(rad).toFloat() * endDist
                            ),
                            strokeWidth = 1.5f
                        )
                    }
                    
                    // Additional wrinkle details
                    repeat(6) { i ->
                        val offsetX = (i % 3 - 1) * radiusPx * 0.4f
                        val offsetY = (i / 3 - 0.5f) * radiusPx * 0.4f
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.15f),
                            radius = radiusPx * 0.15f,
                            center = Offset(ball.position.x + offsetX, ball.position.y + offsetY)
                        )
                    }

                    // Outline with Stroke style
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.6f),
                        radius = radiusPx,
                        center = ball.position,
                        style = Stroke(width = 2f)
                    )
                    
                    // Highlight for shininess
                    drawCircle(
                        color = Color.White.copy(alpha = 0.4f),
                        radius = radiusPx * 0.3f,
                        center = Offset(ball.position.x - radiusPx * 0.3f, ball.position.y - radiusPx * 0.3f)
                    )
                }
            }
        }

        // Score display - simple and clean on notepad
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Text(
                "${gameState.score}",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Instructions overlay - simplified
        if (gameState.attempts == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    "Flick the paper ball into the cup!",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF6B5D3F),
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.8f),
                            MaterialTheme.shapes.medium
                        )
                        .padding(16.dp)
                )
            }
        }

        // Message overlay - simplified
        gameState.showMessage?.let { msg ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    msg,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (msg.contains("✓") || msg.contains("🔥")) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.9f),
                            MaterialTheme.shapes.large
                        )
                        .padding(32.dp)
                )
            }
        }

        // Touch handler
        var dragStart by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (!gameState.isAnimating &&
                                gameState.ball.position.minus(offset).getDistance() < 100f) {
                                dragStart = offset
                                gameState = gameState.copy(
                                    isDragging = true,
                                    dragStart = offset
                                )
                            }
                        },
                        onDrag = { _, dragAmount ->
                            if (!gameState.isAnimating && gameState.isDragging) {
                                val newX = (gameState.ball.position.x + dragAmount.x)
                                    .coerceIn(50f, screenWidthPx - 50f)
                                val newY = (gameState.ball.position.y + dragAmount.y)
                                    .coerceIn(100f, screenHeightPx * 0.9f)
                                gameState = gameState.copy(
                                    ball = gameState.ball.copy(position = Offset(newX, newY))
                                )
                            }
                        },
                        onDragEnd = {
                            if (!gameState.isAnimating && gameState.isDragging) {
                                val power = 8f
                                val throwVelocity = Offset(
                                    (gameState.dragStart.x - gameState.ball.position.x) * power,
                                    (gameState.dragStart.y - gameState.ball.position.y) * power
                                )

                                gameState = gameState.copy(
                                    ball = gameState.ball.copy(
                                        velocity = throwVelocity,
                                        inHand = false
                                    ),
                                    isAnimating = true,
                                    isDragging = false
                                )
                            }
                        }
                    )
                }
        )
    }
}

// Helper extensions
private fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
private fun Offset.getDistance() = sqrt(x * x + y * y)