package com.fares.glass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** What sits behind the glass: the pre-blurred wallpaper and the screen size. */
class Backdrop {
    var frosted by mutableStateOf<ImageBitmap?>(null)
    var screen by mutableStateOf(IntSize.Zero)
    /** Cut-out of the wallpaper's front mountains, drawn over the clock for a depth effect. */
    var foreground by mutableStateOf<ImageBitmap?>(null)
    /** File of the live (video) wallpaper, when one is chosen. */
    var video by mutableStateOf<String?>(null)
    /** False while Glass is in the background, so the video player is released. */
    var playing by mutableStateOf(true)
}

/**
 * "Liquid Glass" material. The background is static, so instead of blurring in real time (impossible
 * before Android 12) it draws the exact part of the pre-blurred background under the panel, then a
 * tint, a specular highlight at the top, an inner glow and a bright rim.
 */
@Composable
fun Glass(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    tint: Color = Color.White.copy(alpha = 0.07f),
    /** Blurred image to sample when it isn't the wallpaper (Top Shelf ambience). */
    source: ImageBitmap? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithCache {
                val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
                // Newer Liquid Glass: clearer body, a hot rim where light enters (top-left) and
                // exits (bottom-right), a second faint inner rim for thickness, a curved specular
                // highlight and a little light pooling along the bottom edge.
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.95f),
                    0.28f to Color.White.copy(alpha = 0.22f),
                    0.62f to Color.White.copy(alpha = 0.08f),
                    1f to Color.White.copy(alpha = 0.60f),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val inset = 2.4.dp.toPx()
                val innerPath = Path().apply {
                    addOutline(shape.createOutline(Size(size.width - 2 * inset, size.height - 2 * inset), layoutDirection, this@drawWithCache))
                    translate(Offset(inset, inset))
                }
                val innerRim = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.35f), 0.5f to Color.White.copy(alpha = 0.04f), 1f to Color.White.copy(alpha = 0.18f),
                )
                val specular = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(size.width * 0.28f, -size.height * 0.15f),
                    radius = maxOf(size.width * 0.55f, size.height * 1.2f),
                )
                val caustic = Brush.verticalGradient(
                    0.72f to Color.Transparent, 1f to Color.White.copy(alpha = 0.10f),
                )
                val innerGlow = Brush.radialGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.07f)),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = maxOf(size.width, size.height) * 0.75f,
                )
                val rimWidth = 1.4.dp.toPx()
                val bevel = 2.dp.toPx()
                onDrawBehind {
                    clipPath(path) {
                        (source ?: backdrop.frosted)?.let { img ->
                            drawImage(
                                img,
                                dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                                dstSize = backdrop.screen,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                        drawRect(tint)
                        drawRect(innerGlow)
                        drawRect(specular)
                        drawRect(caustic)
                        drawPath(innerPath, innerRim, style = Stroke(0.9.dp.toPx()))
                        // Bevel: a bright inner top edge and a soft inner shadow at the bottom.
                        translate(0f, bevel) { drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(bevel * 1.4f)) }
                        translate(0f, -bevel) { drawPath(path, Color.Black.copy(alpha = 0.14f), style = Stroke(bevel * 1.6f)) }
                    }
                    drawPath(path, rim, style = Stroke(rimWidth))
                }
            },
        content = content,
    )
}
