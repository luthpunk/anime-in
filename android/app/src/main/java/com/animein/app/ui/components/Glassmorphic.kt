package com.animein.app.ui.components

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.animein.app.ui.theme.DefaultAccentPreset
import com.animein.app.ui.theme.Hairline
import com.animein.app.ui.theme.HairlineStrong
import com.animein.app.ui.theme.OutlineGrey
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3

/**
 * Tonal surface brush (backward-compatible with existing screens while aligned to Cool Graphite).
 */
fun frostGlassBgBrush(isFocused: Boolean = false): Brush {
    return if (isFocused) {
        Brush.linearGradient(
            colors = listOf(Surface3, Surface2)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Surface2, Surface1)
        )
    }
}

/**
 * Clean hairline / focused border brush (backward-compatible).
 */
fun frostGlassBorderBrush(isFocused: Boolean = false, isSubtle: Boolean = false): Brush {
    return when {
        isFocused -> Brush.linearGradient(
            colors = listOf(
                DefaultAccentPreset.accent,
                DefaultAccentPreset.accent.copy(alpha = 0.85f)
            )
        )
        isSubtle -> Brush.linearGradient(
            colors = listOf(Hairline, Hairline)
        )
        else -> Brush.linearGradient(
            colors = listOf(HairlineStrong, Hairline)
        )
    }
}

/**
 * Applies rounded clip, Cool Graphite tonal background, and crisp hairline/accent border.
 */
fun Modifier.frostGlass(
    shape: Shape = RoundedCornerShape(14.dp),
    isFocused: Boolean = false,
    borderWidth: Dp = 1.dp,
    isSubtle: Boolean = false
): Modifier = this
    .clip(shape)
    .background(
        color = if (isFocused) Surface3 else Surface2,
        shape = shape
    )
    .border(
        width = if (isFocused) 2.dp else borderWidth,
        brush = frostGlassBorderBrush(isFocused = isFocused, isSubtle = isSubtle),
        shape = shape
    )

/**
 * Applies rounded clip and hairline/accent outline border for poster/media cards.
 */
fun Modifier.frostPosterFrame(
    shape: Shape = RoundedCornerShape(14.dp),
    isFocused: Boolean = false,
    surfaceColor: Color = Surface2
): Modifier = this
    .clip(shape)
    .background(color = surfaceColor, shape = shape)
    .border(
        width = if (isFocused) 2.5.dp else 1.dp,
        brush = frostGlassBorderBrush(isFocused = isFocused, isSubtle = !isFocused),
        shape = shape
    )

fun squircleShape(percent: Int = 30): Shape {
    return RoundedCornerShape(percent)
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
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
            .background(Surface2)
            .border(
                width = 1.dp,
                color = HairlineStrong,
                shape = squircleShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(squircleShape(26))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

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
    color: Color = DefaultAccentPreset.accent,
    size: Dp = 44.dp,
    strokeWidth: Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val halfStroke = strokePx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            width = this.size.width - strokePx,
            height = this.size.height - strokePx
        )
        val topLeft = androidx.compose.ui.geometry.Offset(halfStroke, halfStroke)
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}
