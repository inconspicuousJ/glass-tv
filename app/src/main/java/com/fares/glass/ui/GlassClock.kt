package com.fares.glass.ui

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Lock-screen clock made of Liquid Glass. The digits are real outlines (tall, stretched rounded
 * numerals); inside each one we draw the same pre-blurred wallpaper the dock uses, lined up with
 * the screen, then a light body tint, a specular highlight and a bright rim, plus a soft shadow.
 */
@Composable
fun GlassClock(time: String, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val face = remember {
        runCatching { Typeface.createFromAsset(context.assets, "fonts/Clock-Bold.ttf") }.getOrDefault(Typeface.DEFAULT_BOLD)
    }
    val stretch = 1.9f
    val glyphs = remember(time, density.density) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = with(density) { 108.sp.toPx() }
            letterSpacing = -0.01f
        }
        val p = android.graphics.Path()
        paint.getTextPath(time, 0, time.length, 0f, 0f, p)
        p.transform(Matrix().apply { postScale(1f, stretch) })
        val b = RectF()
        @Suppress("DEPRECATION") p.computeBounds(b, true)
        p.offset(-b.left, -b.top)
        Triple(p, b.width(), b.height())
    }
    val (native, w, h) = glyphs
    val path = remember(native) { native.asComposePath() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }

    Box(
        modifier
            .size(wDp, hDp)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithCache {
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.98f),
                    0.3f to Color.White.copy(alpha = 0.40f),
                    0.65f to Color.White.copy(alpha = 0.20f),
                    1f to Color.White.copy(alpha = 0.75f),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val body = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.42f),
                    0.5f to Color.White.copy(alpha = 0.20f),
                    1f to Color.White.copy(alpha = 0.30f),
                )
                val specular = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(size.width * 0.28f, 0f), radius = size.width * 0.6f,
                )
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(90, 0, 0, 0)
                    maskFilter = BlurMaskFilter(16.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                val drop = 7.dp.toPx()
                val rimWidth = 1.6.dp.toPx()
                val bevel = 2.4.dp.toPx()
                val fringe = 0.9.dp.toPx()
                val centre = Offset(size.width / 2, size.height / 2)
                val bevelLight = Color.White.copy(alpha = 0.55f)
                val bevelShade = Color(0xFF07130F).copy(alpha = 0.30f)
                onDrawBehind {
                    drawIntoCanvas { c ->
                        c.nativeCanvas.save()
                        c.nativeCanvas.translate(0f, drop)
                        c.nativeCanvas.drawPath(native, shadow)
                        c.nativeCanvas.restore()
                    }
                    clipPath(path) {
                        backdrop.frosted?.let { img ->
                            drawImage(
                                img,
                                dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                                dstSize = backdrop.screen,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                        // Lens: a slightly magnified copy of what's behind, as thick glass bends light.
                        backdrop.frosted?.let { img ->
                            withTransform({ scale(1.07f, 1.07f, centre) }) {
                                drawImage(
                                    img,
                                    dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                                    dstSize = backdrop.screen,
                                    alpha = 0.45f,
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                        }
                        drawRect(body)
                        drawRect(specular)
                        // Bevel: light catching the inner top edges, shadow along the inner bottom.
                        translate(0f, bevel) { drawPath(path, bevelLight, style = Stroke(bevel * 1.3f)) }
                        translate(0f, -bevel) { drawPath(path, bevelShade, style = Stroke(bevel * 1.6f)) }
                    }
                    // Faint colour fringes, like light splitting at a glass edge.
                    translate(-fringe, 0f) { drawPath(path, Color(0xFF8FE3FF).copy(alpha = 0.22f), style = Stroke(rimWidth)) }
                    translate(fringe, 0f) { drawPath(path, Color(0xFFFFA8D8).copy(alpha = 0.18f), style = Stroke(rimWidth)) }
                    drawPath(path, rim, style = Stroke(rimWidth))
                }
            },
    )
}
