package com.example.paperball.ui.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.paperball.models.*
import com.example.paperball.ui.components.SplashScreen
import com.example.paperball.ui.components.*
import com.example.paperball.ui.components.*
import com.example.paperball.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

@Composable
fun EnhancedPaperBallGame() {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val prefsManager = remember { PreferencesManager(context) }
    var showSplash by remember { mutableStateOf(!prefsManager.hasSeenSplash) }
    
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val ballRadiusPx = with(density) { 24.dp.toPx() }
    
    // Load saved cup position or use default
    val savedCupX = prefsManager.cupPositionX
    val savedCupY = prefsManager.cupPositionY
    val defaultCupX = screenWidthPx * 0.55f
    val defaultCupY = screenHeightPx * 0.4f
    
    var gameState by remember {
        mutableStateOf(GameState(
            ball = Ball(position = Offset(screenWidthPx * 0.2f, screenHeightPx * 0.8f)),
            bin = Bin(position = Offset(
                if (savedCupX > 0) savedCupX else defaultCupX,
                if (savedCupY > 0) savedCupY else defaultCupY
            )),
            highScore = prefsManager.highScore,
            bestStreak = prefsManager.bestStreak
        ))
    }
    
    // Show splash screen first
    if (showSplash) {
        SplashScreen(
            onDismiss = {
                showSplash = false
                prefsManager.hasSeenSplash = true
            }
        )
        return
    }
    
    var isDraggingCup by remember { mutableStateOf(false) }
    
    // Haptic feedback helper
    fun vibrate(duration: Long = 50) {
        if (prefsManager.hapticEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }
    
    // Trail effect
    LaunchedEffect(gameState.isAnimating) {
        if (gameState.isAnimating) {
            while (gameState.isAnimating) {
                gameState = gameState.copy(
                    trailPoints = (gameState.trailPoints + TrailPoint(gameState.ball.position))
                        .takeLast(15)
                        .mapIndexed { index, point ->
                            point.copy(alpha = (index + 1) / 15f * 0.5f)
                        }
                )
                delay(32)
            }
        } else {
            gameState = gameState.copy(trailPoints = emptyList())
        }
    }
    
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

    // Physics simulation with all enhancements
    LaunchedEffect(gameState.isAnimating) {
        if (gameState.isAnimating && !gameState.ball.inHand) {
            var vx = gameState.ball.velocity.x
            var vy = gameState.ball.velocity.y
            var x = gameState.ball.position.x
            var y = gameState.ball.position.y
            var rotation = gameState.ball.rotation
            val gravity = 2500f
            val drag = 0.99f
            val bounceDamping = 0.6f
            
            val binX = gameState.bin.position.x
            val binY = gameState.bin.position.y
            val cupWidth = 250f
            val rimHeight = 35f
            
            var hasScored = false
            var hasMissed = false

            while (y < screenHeightPx + 100 && !hasScored && !hasMissed) {
                vy += gravity * 0.016f
                vx *= drag
                x += vx * 0.016f
                y += vy * 0.016f
                rotation += (vx * 0.5f + vy * 0.3f) * 0.016f

                // Top boundary
                if (y < ballRadiusPx && vy < 0) {
                    vy = -vy * bounceDamping
                    y = ballRadiusPx
                    vibrate(30)
                }

                // Wall bounces
                if (x < ballRadiusPx) {
                    vx = -vx * bounceDamping
                    x = ballRadiusPx
                    vibrate(30)
                } else if (x > screenWidthPx - ballRadiusPx) {
                    vx = -vx * bounceDamping
                    x = screenWidthPx - ballRadiusPx
                    vibrate(30)
                }

                // Floor bounce
                if (y > screenHeightPx * 0.85f - ballRadiusPx && vy > 0) {
                    if (abs(vy) > 100f) {
                        vy = -vy * bounceDamping
                        y = screenHeightPx * 0.85f - ballRadiusPx
                        vx *= 0.9f
                        vibrate(40)
                    } else {
                        hasMissed = true
                    }
                }

                // Cup collision check
                val inBin = x in (binX + 20f)..(binX + cupWidth - 20f) &&
                        y in (binY + rimHeight - 10f)..(binY + rimHeight + 35f) &&
                        vy > 0
                
                // Perfect shot (center of cup)
                val cupCenterX = binX + cupWidth / 2f
                val distanceFromCenter = abs(x - cupCenterX)
                val isPerfectShot = inBin && distanceFromCenter < 30f

                if (inBin) {
                    hasScored = true
                    val newStreak = gameState.currentStreak + 1
                    val newScore = gameState.score + 1
                    val bonusPoints = if (isPerfectShot) 2 else 1
                    
                    // Celebration particles
                    val particles = List(if (isPerfectShot) 30 else 20) {
                        Particle(
                            position = Offset(cupCenterX, binY + rimHeight),
                            velocity = Offset(
                                Random.nextFloat() * 12f - 6f,
                                Random.nextFloat() * -18f - 5f
                            ),
                            color = listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFF6B6B),
                                Color(0xFF4ECDC4),
                                Color(0xFF95E1D3),
                                Color(0xFFFFEB3B)
                            ).random(),
                            size = if (isPerfectShot) 10f else 8f
                        )
                    }
                    
                    // Update stats
                    prefsManager.totalScores += 1
                    if (newScore > prefsManager.highScore) {
                        prefsManager.highScore = newScore
                    }
                    if (newStreak > prefsManager.bestStreak) {
                        prefsManager.bestStreak = newStreak
                    }
                    
                    gameState = gameState.copy(
                        score = newScore,
                        highScore = prefsManager.highScore,
                        currentStreak = newStreak,
                        bestStreak = prefsManager.bestStreak,
                        perfectShots = if (isPerfectShot) gameState.perfectShots + 1 else gameState.perfectShots,
                        showMessage = when {
                            isPerfectShot -> "🎯 PERFECT! +$bonusPoints"
                            newStreak >= 5 -> "🔥 ${newStreak}x STREAK!"
                            else -> "✓ Scored!"
                        },
                        particles = particles
                    )
                    
                    vibrate(if (isPerfectShot) 100 else 70)
                }

                gameState = gameState.copy(
                    ball = gameState.ball.copy(
                        position = Offset(x, y),
                        velocity = Offset(vx, vy),
                        rotation = rotation
                    )
                )
                delay(16)
            }
            
            if (hasMissed) {
                gameState = gameState.copy(
                    currentStreak = 0,
                    showMessage = "✗ Missed!"
                )
                vibrate(200)
            }

            delay(1000)
            
            // Update total shots
            prefsManager.totalShots += 1
            
            // Reset ball
            gameState = gameState.copy(
                ball = Ball(position = Offset(screenWidthPx * 0.2f, screenHeightPx * 0.8f)),
                isAnimating = false,
                attempts = gameState.attempts + 1,
                showMessage = null
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4E04D))
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

        // Checkered floor
        Canvas(modifier = Modifier.fillMaxSize()) {
            val floorY = size.height * 0.85f
            val checkSize = 30f
            val checksX = (size.width / checkSize).toInt() + 1
            repeat(checksX) { i ->
                drawRect(
                    color = if (i % 2 == 0) Color.Black else Color.White,
                    topLeft = Offset(i * checkSize, floorY),
                    size = androidx.compose.ui.geometry.Size(checkSize, checkSize)
                )
            }
        }

        // Ball trail effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            gameState.trailPoints.forEach { point ->
                drawCircle(
                    color = Color.Gray.copy(alpha = point.alpha * 0.3f),
                    radius = ballRadiusPx * 0.8f,
                    center = point.position
                )
            }
        }

        // Enhanced White Coffee Cup
        val binX = gameState.bin.position.x
        val binY = gameState.bin.position.y
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cupWidth = 250f
            val cupHeight = 270f
            val cupBottom = binY + cupHeight
            val cupCenterX = binX + cupWidth / 2f
            
            // Enhanced shadow with gradient effect
            drawOval(
                color = Color.Black.copy(alpha = 0.2f),
                topLeft = Offset(binX - 15f, cupBottom - 8f),
                size = androidx.compose.ui.geometry.Size(cupWidth + 30f, 30f)
            )
            
            // Cup body - main trapezoid
            val cupPath = Path().apply {
                moveTo(binX + 35f, cupBottom)
                lineTo(binX, binY + 30f)
                lineTo(binX + cupWidth, binY + 30f)
                lineTo(binX + cupWidth - 35f, cupBottom)
                close()
            }
            
            // Base white color
            drawPath(path = cupPath, color = Color(0xFFFAFAFA))
            
            // Left side shading (darker)
            val leftShade = Path().apply {
                moveTo(binX + 35f, cupBottom)
                lineTo(binX, binY + 30f)
                lineTo(binX + 25f, binY + 30f)
                lineTo(binX + 45f, cupBottom)
                close()
            }
            drawPath(path = leftShade, color = Color(0xFFDDDDDD))
            
            // Right side highlight (brighter)
            val rightHighlight = Path().apply {
                moveTo(binX + cupWidth - 35f, cupBottom)
                lineTo(binX + cupWidth, binY + 30f)
                lineTo(binX + cupWidth - 25f, binY + 30f)
                lineTo(binX + cupWidth - 45f, cupBottom)
                close()
            }
            drawPath(path = rightHighlight, color = Color.White)
            
            // Center highlight stripe for glossy effect
            val centerHighlight = Path().apply {
                moveTo(cupCenterX - 15f, cupBottom - 20f)
                lineTo(cupCenterX - 10f, binY + 50f)
                lineTo(cupCenterX + 10f, binY + 50f)
                lineTo(cupCenterX + 15f, cupBottom - 20f)
                close()
            }
            drawPath(path = centerHighlight, color = Color.White.copy(alpha = 0.3f))
            
            // Vertical texture lines for realism
            repeat(4) { i ->
                val xOffset = binX + 60f + i * 40f
                val topX = binX + 20f + i * 50f
                drawLine(
                    color = Color(0xFFEEEEEE),
                    start = Offset(topX, binY + 40f),
                    end = Offset(xOffset, cupBottom - 30f),
                    strokeWidth = 1.5f
                )
            }
            
            // Rim - outer ellipse (thicker, more prominent)
            drawOval(
                color = Color(0xFFE5E5E5),
                topLeft = Offset(binX - 8f, binY + 22f),
                size = androidx.compose.ui.geometry.Size(cupWidth + 16f, 24f)
            )
            
            // Rim - middle layer
            drawOval(
                color = Color(0xFFD8D8D8),
                topLeft = Offset(binX + 2f, binY + 26f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 4f, 18f)
            )
            
            // Inner rim (darker for depth)
            drawOval(
                color = Color(0xFFC0C0C0),
                topLeft = Offset(binX + 10f, binY + 29f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 20f, 14f)
            )
            
            // Cup opening (very dark inside)
            drawOval(
                color = Color(0xFF2A2A2A),
                topLeft = Offset(binX + 20f, binY + 31f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 40f, 12f)
            )
            
            // Rim top highlight (glossy effect)
            drawOval(
                color = Color.White.copy(alpha = 0.7f),
                topLeft = Offset(binX + 15f, binY + 23f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 30f, 10f)
            )
            
            // Bottom rim of cup (where it meets the base)
            drawOval(
                color = Color(0xFFD0D0D0),
                topLeft = Offset(binX + 30f, cupBottom - 8f),
                size = androidx.compose.ui.geometry.Size(cupWidth - 60f, 16f)
            )
            
            // Subtle brand/logo area (optional decorative circle)
            drawCircle(
                color = Color(0xFFEEEEEE),
                radius = 25f,
                center = Offset(cupCenterX, binY + cupHeight * 0.6f)
            )
            
            // Inner circle for logo
            drawCircle(
                color = Color(0xFFE0E0E0),
                radius = 20f,
                center = Offset(cupCenterX, binY + cupHeight * 0.6f)
            )
            
            // Simple coffee cup icon in the circle
            drawLine(
                color = Color(0xFFB0B0B0),
                start = Offset(cupCenterX - 8f, binY + cupHeight * 0.6f - 5f),
                end = Offset(cupCenterX + 8f, binY + cupHeight * 0.6f - 5f),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFFB0B0B0),
                start = Offset(cupCenterX - 6f, binY + cupHeight * 0.6f),
                end = Offset(cupCenterX + 6f, binY + cupHeight * 0.6f),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFFB0B0B0),
                start = Offset(cupCenterX - 4f, binY + cupHeight * 0.6f + 5f),
                end = Offset(cupCenterX + 4f, binY + cupHeight * 0.6f + 5f),
                strokeWidth = 2f
            )
        }

        // Flick indicator when dragging
        if (gameState.isDragging && !gameState.isAnimating) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val power = 8f
                val throwVelocity = Offset(
                    (gameState.dragStart.x - gameState.ball.position.x) * power,
                    (gameState.dragStart.y - gameState.ball.position.y) * power
                )
                
                val arrowLength = 80f
                val arrowAngle = atan2(throwVelocity.y, throwVelocity.x)
                val arrowEndX = gameState.ball.position.x + cos(arrowAngle) * arrowLength
                val arrowEndY = gameState.ball.position.y + sin(arrowAngle) * arrowLength
                
                // Dashed arrow
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
                val perpAngle = arrowAngle + PI.toFloat() / 2f
                val arrowHeadPath = Path().apply {
                    moveTo(arrowEndX, arrowEndY)
                    lineTo(
                        arrowEndX - cos(arrowAngle) * arrowHeadSize + cos(perpAngle) * arrowHeadSize / 2f,
                        arrowEndY - sin(arrowAngle) * arrowHeadSize + sin(perpAngle) * arrowHeadSize / 2f
                    )
                    lineTo(
                        arrowEndX - cos(arrowAngle) * arrowHeadSize - cos(perpAngle) * arrowHeadSize / 2f,
                        arrowEndY - sin(arrowAngle) * arrowHeadSize - sin(perpAngle) * arrowHeadSize / 2f
                    )
                    close()
                }
                drawPath(path = arrowHeadPath, color = Color(0xFF6B5D3F))
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

        // Paper ball with shadow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ball = gameState.ball
            val radiusPx = ballRadiusPx

            rotate(ball.rotation, pivot = ball.position) {
                // Shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.2f),
                    radius = radiusPx * 1.1f,
                    center = Offset(ball.position.x + 5f, ball.position.y + 5f)
                )
                
                // Main ball
                drawCircle(
                    color = Color(0xFFF5F5F5),
                    radius = radiusPx,
                    center = ball.position
                )
                
                // 3D shading
                drawCircle(
                    color = Color(0xFFE0E0E0),
                    radius = radiusPx * 0.7f,
                    center = Offset(ball.position.x + radiusPx * 0.2f, ball.position.y + radiusPx * 0.2f)
                )

                // Crumpled texture
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
                
                repeat(6) { i ->
                    val offsetX = (i % 3 - 1) * radiusPx * 0.4f
                    val offsetY = (i / 3 - 0.5f) * radiusPx * 0.4f
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.15f),
                        radius = radiusPx * 0.15f,
                        center = Offset(ball.position.x + offsetX, ball.position.y + offsetY)
                    )
                }

                drawCircle(
                    color = Color.Gray.copy(alpha = 0.6f),
                    radius = radiusPx,
                    center = ball.position,
                    style = Stroke(width = 2f)
                )
                
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = radiusPx * 0.3f,
                    center = Offset(ball.position.x - radiusPx * 0.3f, ball.position.y - radiusPx * 0.3f)
                )
            }
        }

        // Top Score Display (centered, no buttons)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${gameState.score}",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                if (gameState.currentStreak > 1) {
                    Text(
                        "🔥 ${gameState.currentStreak}x",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        }

        // Bottom info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Best: ${gameState.highScore}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B5D3F),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
            
            Text(
                "Streak: ${gameState.bestStreak}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B5D3F),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
        }

        // Message overlay
        gameState.showMessage?.let { msg ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    msg,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (msg.contains("✓") || msg.contains("🔥") || msg.contains("🎯")) 
                        Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.9f), MaterialTheme.shapes.large)
                        .padding(32.dp)
                )
            }
        }

        // Touch handler for ball and cup
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cupWidth = 250f
                            val cupHeight = 270f
                            val binX = gameState.bin.position.x
                            val binY = gameState.bin.position.y
                            
                            // Check if touching cup
                            val touchingCup = offset.x in (binX)..(binX + cupWidth) &&
                                    offset.y in (binY)..(binY + cupHeight)
                            
                            // Check if touching ball
                            val touchingBall = gameState.ball.position.minus(offset).getDistance() < 100f
                            
                            if (!gameState.isAnimating && !gameState.isPaused) {
                                if (touchingCup) {
                                    isDraggingCup = true
                                    vibrate(20)
                                } else if (touchingBall) {
                                    gameState = gameState.copy(
                                        isDragging = true,
                                        dragStart = offset
                                    )
                                    vibrate(20)
                                }
                            }
                        },
                        onDrag = { _, dragAmount ->
                            if (isDraggingCup) {
                                // Drag cup
                                val cupWidth = 250f
                                val cupHeight = 270f
                                val newX = (gameState.bin.position.x + dragAmount.x)
                                    .coerceIn(0f, screenWidthPx - cupWidth)
                                val newY = (gameState.bin.position.y + dragAmount.y)
                                    .coerceIn(50f, screenHeightPx * 0.85f - cupHeight)
                                gameState = gameState.copy(
                                    bin = gameState.bin.copy(position = Offset(newX, newY))
                                )
                            } else if (!gameState.isAnimating && gameState.isDragging) {
                                // Drag ball
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
                            if (isDraggingCup) {
                                // Save cup position
                                prefsManager.cupPositionX = gameState.bin.position.x
                                prefsManager.cupPositionY = gameState.bin.position.y
                                isDraggingCup = false
                                vibrate(30)
                            } else if (!gameState.isAnimating && gameState.isDragging) {
                                // Throw ball
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
                                vibrate(50)
                            }
                        }
                    )
                }
        )

        // Pause menu
        if (gameState.isPaused) {
            PauseMenu(
                onResume = { gameState = gameState.copy(isPaused = false) },
                onRestart = {
                    gameState = GameState(
                        ball = Ball(position = Offset(screenWidthPx * 0.2f, screenHeightPx * 0.8f)),
                        bin = Bin(position = Offset(
                            if (prefsManager.cupPositionX > 0) prefsManager.cupPositionX else defaultCupX,
                            if (prefsManager.cupPositionY > 0) prefsManager.cupPositionY else defaultCupY
                        )),
                        highScore = prefsManager.highScore,
                        bestStreak = prefsManager.bestStreak
                    )
                },
                onSettings = {
                    gameState = gameState.copy(
                        isPaused = false,
                        showSettings = true
                    )
                },
                onStats = {
                    gameState = gameState.copy(
                        isPaused = false,
                        showStats = true
                    )
                }
            )
        }

        // Stats screen
        if (gameState.showStats) {
            StatsScreen(
                highScore = prefsManager.highScore,
                bestStreak = prefsManager.bestStreak,
                totalShots = prefsManager.totalShots,
                totalScores = prefsManager.totalScores,
                accuracy = prefsManager.accuracy,
                onClose = { gameState = gameState.copy(showStats = false) }
            )
        }

        // Settings screen
        if (gameState.showSettings) {
            SettingsScreen(
                soundEnabled = prefsManager.soundEnabled,
                hapticEnabled = prefsManager.hapticEnabled,
                onSoundToggle = { prefsManager.soundEnabled = it },
                onHapticToggle = { prefsManager.hapticEnabled = it },
                onClose = { gameState = gameState.copy(showSettings = false) }
            )
        }
    }
}

// Helper extensions
private fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
private fun Offset.getDistance() = sqrt(x * x + y * y)
