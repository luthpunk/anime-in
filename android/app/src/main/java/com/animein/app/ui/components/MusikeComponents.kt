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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.animein.app.ui.theme.AccentPreset
import com.animein.app.ui.theme.AccentPresets
import com.animein.app.ui.theme.DangerCoral
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.FgSecondary
import com.animein.app.ui.theme.FgTertiary
import com.animein.app.ui.theme.Hairline
import com.animein.app.ui.theme.HairlineStrong
import com.animein.app.ui.theme.HighlightTop
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.MonoTextStyle
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3
import com.animein.app.ui.theme.Surface4

// ── Shape Constants (Musike single-shape rule) ─────────────────────────────
val RadiusPanel = 20.dp
val RadiusTile = 14.dp
val RadiusArt = 10.dp

/**
 * Floating Island container with nested bezel (outer Surface1 + hairline, inner Surface2 + top sheen),
 * inspired by a-music's `.island` / `.island-core`.
 */
@Composable
fun IslandPanel(
    modifier: Modifier = Modifier,
    outerRadius: Dp = RadiusPanel,
    bezelPadding: Dp = 5.dp,
    useNestedCore: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val outerShape = RoundedCornerShape(outerRadius)
    val innerRadius = (outerRadius - bezelPadding).coerceAtLeast(8.dp)
    val innerShape = RoundedCornerShape(innerRadius)

    Box(
        modifier = modifier
            .clip(outerShape)
            .background(Surface1, outerShape)
            .border(1.dp, Hairline, outerShape)
            .then(if (useNestedCore) Modifier.padding(bezelPadding) else Modifier)
    ) {
        if (useNestedCore) {
            Box(
                modifier = Modifier
                    .clip(innerShape)
                    .background(Surface2, innerShape)
                    .drawBehind {
                        drawLine(
                            color = HighlightTop,
                            start = Offset(12f, 0.5f),
                            end = Offset(size.width - 12f, 0.5f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

/**
 * Clean tonal surface modifier with hairline border and inset-safe accent focus ring (zero clip on TV).
 */
fun Modifier.musikeSurface(
    shape: Shape = RoundedCornerShape(RadiusTile),
    isFocused: Boolean = false,
    accentColor: Color = Color(0xFFC3EC52),
    containerColor: Color = Surface2,
    focusedContainerColor: Color = Surface3,
    isActive: Boolean = false
): Modifier = this
    .clip(shape)
    .background(
        color = when {
            isFocused -> focusedContainerColor
            isActive -> Surface3
            else -> containerColor
        },
        shape = shape
    )
    .border(
        width = if (isFocused) 2.5.dp else 1.dp,
        color = when {
            isFocused -> accentColor
            isActive -> accentColor.copy(alpha = 0.45f)
            else -> Hairline
        },
        shape = shape
    )

/**
 * Poster frame modifier (`14.dp` radius, `Surface2` placeholder, hairline border, crisp accent focus ring).
 */
fun Modifier.musikePosterFrame(
    shape: Shape = RoundedCornerShape(RadiusTile),
    isFocused: Boolean = false,
    accentColor: Color = Color(0xFFC3EC52)
): Modifier = this
    .clip(shape)
    .background(Surface2, shape)
    .border(
        width = if (isFocused) 2.5.dp else 1.dp,
        color = if (isFocused) accentColor else Hairline,
        shape = shape
    )

/**
 * AuraNime 5-Facet Origami Fox Head abstract mark rendered dynamically in the active accent color.
 */
@Composable
fun BrandLogoMark(
    modifier: Modifier = Modifier,
    color: Color = LocalAccentPreset.current.accent,
    size: Dp = 24.dp
) {
    val bottomColor = remember(color) {
        // Subtle cool-mint/deep shift toward the snout while preserving the active accent family
        Color(
            red = (color.red * 0.55f + 0.08f).coerceIn(0f, 1f),
            green = (color.green * 0.92f + 0.08f).coerceIn(0f, 1f),
            blue = (color.blue * 0.85f + 0.15f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Map normalized coords (26..82 -> 0..w, 24..82 -> 0..h) so the mark fills cleanly
        fun x(v: Float): Float = ((v - 26f) / 56f) * w
        fun y(v: Float): Float = ((v - 24f) / 58f) * h

        val brush = Brush.verticalGradient(
            colors = listOf(color, bottomColor),
            startY = 0f,
            endY = h
        )

        // 1. Left Ear
        val leftEar = Path().apply {
            moveTo(x(31f), y(25f))
            lineTo(x(43f), y(37f))
            lineTo(x(32f), y(46f))
            close()
        }
        // 2. Right Ear
        val rightEar = Path().apply {
            moveTo(x(77f), y(25f))
            lineTo(x(65f), y(37f))
            lineTo(x(76f), y(46f))
            close()
        }
        // 3. Center Forehead & Snout Bridge
        val center = Path().apply {
            moveTo(x(54f), y(43f))
            lineTo(x(45f), y(39f))
            lineTo(x(35f), y(48f))
            lineTo(x(46f), y(56f))
            lineTo(x(54f), y(79f))
            lineTo(x(62f), y(56f))
            lineTo(x(73f), y(48f))
            lineTo(x(63f), y(39f))
            close()
        }
        // 4. Left Cheek / Jaw
        val leftCheek = Path().apply {
            moveTo(x(33f), y(50f))
            lineTo(x(28f), y(60f))
            lineTo(x(51f), y(81f))
            lineTo(x(44f), y(58f))
            close()
        }
        // 5. Right Cheek / Jaw
        val rightCheek = Path().apply {
            moveTo(x(75f), y(50f))
            lineTo(x(80f), y(60f))
            lineTo(x(57f), y(81f))
            lineTo(x(64f), y(58f))
            close()
        }

        drawPath(leftEar, brush = brush)
        drawPath(rightEar, brush = brush)
        drawPath(center, brush = brush)
        drawPath(leftCheek, brush = brush)
        drawPath(rightCheek, brush = brush)
    }
}

/**
 * Primary solid accent pill button (a-music Play / CTA button style).
 */
@Composable
fun AccentPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 40.dp
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = CircleShape

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(accent.accent, shape)
            .then(
                if (isFocused) {
                    Modifier.border(2.5.dp, Color.White, shape)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                color = accent.onAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Secondary tonal pill button (`Surface2` + hairline, accent ring on focus).
 */
@Composable
fun TonalPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    height: Dp = 36.dp
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(height)
            .musikeSurface(
                shape = CircleShape,
                isFocused = isFocused,
                accentColor = accent.accent,
                containerColor = if (isActive) accent.accentSoft else Surface2,
                focusedContainerColor = Surface3,
                isActive = isActive
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive || isFocused) accent.accent else FgPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                color = if (isActive) accent.accent else FgPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Monospace numeric/label badge (for episodes, ratings, item counts).
 */
@Composable
fun MonoBadge(
    text: String,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false
) {
    val accent = LocalAccentPreset.current
    val bgColor = if (isAccent) accent.accent else Surface2.copy(alpha = 0.88f)
    val fgColor = if (isAccent) accent.onAccent else FgPrimary
    val borderColor = if (isAccent) Color.Transparent else HairlineStrong

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MonoTextStyle.copy(
                color = fgColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Smooth shimmer skeleton block matching a-music's `.skeleton` (`Surface2 -> Surface3 -> Surface2`).
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(RadiusTile)
) {
    val transition = rememberInfiniteTransition(label = "musikeShimmer")
    val translate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Surface2, Surface3, Surface2),
        start = Offset(translate, 0f),
        end = Offset(translate + 320f, 0f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush, shape)
    )
}

/**
 * Unified Musike-style Error State card with icon & accent retry pill.
 */
@Composable
fun MusikeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        IslandPanel(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DangerCoral.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DangerCoral,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Gagal Memuat Data",
                    color = FgPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    color = FgSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                AccentPillButton(
                    text = "Coba Lagi",
                    icon = Icons.Default.Refresh,
                    onClick = onRetry
                )
            }
        }
    }
}

/**
 * Unified Musike-style Empty State view.
 */
@Composable
fun MusikeEmptyState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Surface2)
                    .border(1.dp, Hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FgTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = title,
            color = FgPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = FgSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Appearance / Accent Color Picker Dialog inspired by a-music's Customize view.
 * Works cleanly with both touch (Mobile) and D-pad (Android TV).
 */
@Composable
fun AppearanceSettingsDialog(
    currentAccentId: String,
    onSelectAccent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeAccent = LocalAccentPreset.current

    Dialog(onDismissRequest = onDismiss) {
        IslandPanel(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BrandLogoMark(size = 20.dp)
                        Column {
                            Text(
                                text = "Tampilan & Aksen",
                                color = FgPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pilih warna aksen favoritmu",
                                color = FgSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    var isCloseFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .musikeSurface(
                                shape = CircleShape,
                                isFocused = isCloseFocused,
                                accentColor = activeAccent.accent,
                                containerColor = Surface3
                            )
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = FgPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 2 rows x 4 swatches
                val rows = remember { AccentPresets.chunked(4) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                AccentSwatchItem(
                                    preset = preset,
                                    isSelected = preset.id.equals(currentAccentId, ignoreCase = true),
                                    onClick = { onSelectAccent(preset.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Active preview footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aksen aktif",
                        color = FgSecondary,
                        fontSize = 12.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(activeAccent.accent)
                        )
                        Text(
                            text = activeAccent.label,
                            style = MonoTextStyle.copy(
                                color = activeAccent.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatchItem(
    preset: AccentPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val itemShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(itemShape)
            .background(
                if (isFocused) Surface4 else if (isSelected) Surface3 else Surface1,
                itemShape
            )
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
                color = if (isFocused || isSelected) preset.accent else Hairline,
                shape = itemShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(preset.accent),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Dipilih",
                    tint = preset.onAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = preset.label,
            color = if (isSelected || isFocused) FgPrimary else FgSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ── Crisp Custom Vector Icons (No material-icons-extended dependency) ──────

val MusikeGenreIcon: ImageVector
    get() = ImageVector.Builder(
        name = "MusikeGenre",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        // Top-left square
        moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
        // Top-right square
        moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        // Bottom-left square
        moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
        // Bottom-right square
        moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
    }.build()

val MusikeLibraryIcon: ImageVector
    get() = ImageVector.Builder(
        name = "MusikeLibrary",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        // Bookmark silhouette
        moveTo(6f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 20f)
        lineTo(12f, 15.8f)
        lineTo(6f, 20f)
        close()
    }.build()

/**
 * Helper to draw subtle top specular line inside any card or bar.
 */
fun Modifier.topHairline(color: Color = Hairline): Modifier = this.drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(0f, 0.5f),
        end = Offset(size.width, 0.5f),
        strokeWidth = 1.dp.toPx()
    )
}
