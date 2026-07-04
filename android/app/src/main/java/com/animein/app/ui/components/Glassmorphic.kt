package com.animein.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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

/**
 * Creates a custom squircle shape that resembles Apple's iOS icon shape.
 */
fun squircleShape(percent: Int = 30): Shape {
    return RoundedCornerShape(percent)
}

/**
 * A container that provides a frosted-glass (glassmorphic) appearance.
 * Simulates transparency, specular reflections (via gradient border),
 * and inner depth.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF).copy(alpha = 0.08f),
                        Color(0xFFFFFFFF).copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF).copy(alpha = 0.20f),
                        Color(0xFFFFFFFF).copy(alpha = 0.03f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Apple-style Glassmorphic Icon composable with premium gloss and neon reflections.
 */
@Composable
fun AppleGlassIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconColor: Color = Color.White,
    glowColor: Color = Color(0xFF9E00FF), // NeonPurple
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    
    Box(
        modifier = modifier
            .size(56.dp)
            .then(clickableModifier)
            .clip(squircleShape())
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.Black.copy(alpha = 0.4f)
                    )
                )
            )
            .drawBehind {
                // Drawing an ambient glow/shadow to look 3D
                drawCircle(
                    color = glowColor.copy(alpha = 0.25f),
                    radius = size.minDimension / 1.8f
                )
            }
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.05f),
                        glowColor.copy(alpha = 0.4f)
                    )
                ),
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
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f)
                        )
                    )
                )
        )
        
        // Glowing reflection stripe across the top half (specular gloss)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.02f)
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
