package com.animein.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.FrostGlassBgBottom
import com.animein.app.ui.theme.FrostGlassBgMid
import com.animein.app.ui.theme.FrostGlassBgTop
import com.animein.app.ui.theme.OutlineGrey
import com.animein.app.ui.theme.OutlineGreyHighlight
import com.animein.app.ui.theme.OutlineGreySubtle

/**
 * Gradient background brush for Frost Glass surfaces.
 */
fun frostGlassBgBrush(isFocused: Boolean = false): Brush {
    return if (isFocused) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f),
                FrostGlassBgTop.copy(alpha = 0.90f),
                FrostGlassBgBottom.copy(alpha = 0.95f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                FrostGlassBgTop,
                FrostGlassBgMid,
                FrostGlassBgBottom
            )
        )
    }
}

/**
 * Gradient border brush for Frost Glass outlines (top-left specular highlight -> subtle middle -> bottom-right edge).
 */
fun frostGlassBorderBrush(isFocused: Boolean = false, isSubtle: Boolean = false): Brush {
    return when {
        isFocused -> Brush.linearGradient(
            colors = listOf(
                Color.White,
                Color(0xFFDCDCE6),
                Color.White.copy(alpha = 0.85f)
            )
        )
        isSubtle -> Brush.linearGradient(
            colors = listOf(
                OutlineGreyHighlight.copy(alpha = 0.65f),
                OutlineGreySubtle.copy(alpha = 0.35f),
                OutlineGrey.copy(alpha = 0.55f)
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(
                OutlineGreyHighlight.copy(alpha = 0.85f),
                OutlineGrey.copy(alpha = 0.45f),
                OutlineGrey.copy(alpha = 0.80f)
            )
        )
    }
}

/**
 * Applies rounded clip, layered Frost Glass gradient background, and gradient outline border.
 * Always clips to [shape] first so focus/ripple highlights follow the rounded shape instead of a rectangle.
 */
fun Modifier.frostGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    isFocused: Boolean = false,
    borderWidth: Dp = 1.dp,
    isSubtle: Boolean = false
): Modifier = this
    .clip(shape)
    .background(brush = frostGlassBgBrush(isFocused), shape = shape)
    .border(
        width = if (isFocused) 1.5.dp else borderWidth,
        brush = frostGlassBorderBrush(isFocused = isFocused, isSubtle = isSubtle),
        shape = shape
    )

/**
 * Applies rounded clip and gradient outline border for poster/media cards.
 */
fun Modifier.frostPosterFrame(
    shape: Shape = RoundedCornerShape(16.dp),
    isFocused: Boolean = false,
    surfaceColor: Color = DarkSurface
): Modifier = this
    .clip(shape)
    .background(color = surfaceColor, shape = shape)
    .border(
        width = if (isFocused) 1.8.dp else 1.dp,
        brush = frostGlassBorderBrush(isFocused = isFocused, isSubtle = !isFocused),
        shape = shape
    )

/**
 * Creates a custom squircle shape that resembles Apple's iOS icon shape.
 */
fun squircleShape(percent: Int = 30): Shape {
    return RoundedCornerShape(percent)
}

/**
 * A container that provides a frosted-glass (glassmorphic) appearance.
 * Uses dark grey frosted surface and a gradient grey outline border without neon tint.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    isFocused: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .frostGlass(
                shape = shape,
                isFocused = isFocused,
                borderWidth = borderWidth
            )
    ) {
        content()
    }
}

/**
 * Frosted Glass Icon composable with neutral dark grey frost and grey outline (no neon).
 */
@Composable
fun AppleGlassIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconColor: Color = Color.White,
    glowColor: Color = OutlineGrey,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    
    Box(
        modifier = modifier
            .size(56.dp)
            .then(clickableModifier)
            .clip(squircleShape())
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FrostGlassBgTop,
                        FrostGlassBgBottom
                    )
                )
            )
            .border(
                width = 1.dp,
                color = OutlineGrey,
                shape = squircleShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        // Frosted shine layer inside the squircle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(squircleShape(26))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.20f)
                        )
                    )
                )
        )

        // The actual icon inside
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun PremiumLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    Canvas(modifier = modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
