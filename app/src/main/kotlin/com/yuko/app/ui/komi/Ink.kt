/*
 * Adaptado de Komi Store (kurikomi-labs/komi-store), Apache License 2.0:
 * core/presentation/.../personality/manga/decoration/{InkModifiers, InkPress, InkFocusRing,
 * GridPaper, SpeedLineWash, StarburstShape}.kt. Sin cambios funcionales salvo el empaquetado.
 */
package com.yuko.app.ui.komi

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// Un mosaico de puntos horneado una vez en un ImageBitmap diminuto, expuesto como brush repetido.
private fun dotTileBrush(color: Color, opacity: Float, spacing: Float, radius: Float): ShaderBrush {
    val side = spacing.roundToInt().coerceAtLeast(1)
    val tile = ImageBitmap(side, side)
    val canvas = Canvas(tile)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.copy(alpha = opacity)
    }
    canvas.drawCircle(Offset(side / 2f, side / 2f), radius, paint)
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

fun Modifier.hardShadow(offset: DpOffset, color: Color, shape: Shape = RectangleShape): Modifier =
    drawBehind {
        val dx = offset.x.toPx()
        val dy = offset.y.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(dx, dy) { drawOutline(outline = outline, color = color) }
    }

fun Modifier.hardShadow(color: Color, shape: Shape = RectangleShape, offset: DrawScope.() -> Offset): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val o = offset()
            translate(o.x, o.y) { drawOutline(outline = outline, color = color) }
        }
    }

fun Modifier.screentoneFill(color: Color, opacity: Float, dotRadius: Float = 1f, spacing: Float = 6f): Modifier =
    drawWithCache {
        val brush = dotTileBrush(color, opacity, spacing, dotRadius)
        onDrawBehind { drawRect(brush = brush) }
    }

// Trama de puntos en la esquina superior derecha, con máscara radial que se apaga al 70 % de la diagonal.
fun Modifier.screentoneCorner(
    color: Color,
    opacity: Float,
    boost: Float = 1f,
    regionWidth: Dp = 120.dp,
    regionHeight: Dp = 90.dp,
    dotRadius: Float = 1.1f,
    spacing: Float = 5f,
): Modifier =
    drawWithCache {
        val wPx = regionWidth.toPx()
        val hPx = regionHeight.toPx()
        val maskRadius = hypot(wPx, hPx) * 0.7f
        val bw = wPx.coerceAtMost(size.width).roundToInt().coerceAtLeast(1)
        val bh = hPx.coerceAtMost(size.height).roundToInt().coerceAtLeast(1)
        val region = ImageBitmap(bw, bh)
        val canvas = Canvas(region)
        val paint = Paint().apply { isAntiAlias = true }
        var y = 0f
        while (y <= bh) {
            var x = 0f
            while (x <= bw) {
                val falloff = (1f - hypot(bw - x, y) / maskRadius).coerceIn(0f, 1f)
                if (falloff > 0f) {
                    paint.color = color.copy(alpha = opacity * boost * falloff)
                    canvas.drawCircle(Offset(x, y), dotRadius, paint)
                }
                x += spacing
            }
            y += spacing
        }
        onDrawBehind {
            val left = (size.width - bw).coerceAtLeast(0f)
            drawImage(image = region, topLeft = Offset(left, 0f))
        }
    }

fun Modifier.speedLines(color: Color, spokes: Int = 120, opacity: Float = 0.12f, strokeWidth: Float = 1.2f): Modifier =
    drawWithCache {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val reach = hypot(size.width, size.height)
        onDrawBehind {
            for (i in 0 until spokes) {
                val angle = (2.0 * PI * i / spokes).toFloat()
                drawLine(
                    color = color,
                    start = Offset(cx, cy),
                    end = Offset(cx + cos(angle) * reach, cy + sin(angle) * reach),
                    strokeWidth = strokeWidth,
                    alpha = opacity,
                )
            }
        }
    }

fun Modifier.speedLineWash(color: Color, alpha: Float = 0.16f, spokes: Int = 56): Modifier =
    drawBehind {
        val center = Offset(size.width - 46.dp.toPx(), 4.dp.toPx())
        val reach = 130.dp.toPx()
        for (i in 0 until spokes) {
            val angle = (2.0 * PI * i / spokes).toFloat()
            val end = Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    start = center,
                    end = end,
                ),
                start = center,
                end = end,
                strokeWidth = 1.1f,
            )
        }
    }

fun Modifier.gridPaper(color: Color, opacity: Float, cell: Dp = 26.dp): Modifier =
    drawBehind {
        val step = cell.toPx()
        val stroke = 1.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(color = color, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = stroke, alpha = opacity)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = stroke, alpha = opacity)
            y += step
        }
    }

// Sello que baja al pulsar: la cara se desplaza hacia su sombra mientras la sombra se contrae a cero.
fun Modifier.inkPress(
    pressProgress: () -> Float,
    hoverProgress: () -> Float,
    shadow: DpOffset,
    shadowColor: Color,
    shape: Shape,
    pressInset: Dp = 1.dp,
    hoverLift: Dp = 2.dp,
    hoverGrow: Dp = 3.dp,
): Modifier =
    this
        .offset {
            val pp = pressProgress()
            val hp = hoverProgress()
            val pressX = maxOf(shadow.x.toPx() - pressInset.toPx(), 1.dp.toPx())
            val pressY = maxOf(shadow.y.toPx() - pressInset.toPx(), 1.dp.toPx())
            val lift = -hoverLift.toPx() * hp * (1f - pp)
            IntOffset((pressX * pp + lift).roundToInt(), (pressY * pp + lift).roundToInt())
        }
        .hardShadow(color = shadowColor, shape = shape) {
            val pp = pressProgress()
            val hp = hoverProgress()
            val grow = hoverGrow.toPx() * hp
            Offset(x = (shadow.x.toPx() + grow) * (1f - pp), y = (shadow.y.toPx() + grow) * (1f - pp))
        }

fun Modifier.inkFocusRing(focused: () -> Boolean, color: Color, width: Dp = 3.dp, gap: Dp = 3.dp): Modifier =
    drawWithCache {
        val strokePx = width.toPx()
        val inset = -(gap.toPx() + strokePx / 2f)
        onDrawWithContent {
            drawContent()
            if (focused()) {
                drawRect(
                    color = color,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2f * inset, size.height - 2f * inset),
                    style = Stroke(width = strokePx),
                )
            }
        }
    }

/** Paralelogramo inclinado (sello). */
fun stampShape(skewDegrees: Float): Shape =
    GenericShape { size, _ ->
        val sk = size.height * tan(skewDegrees * (PI / 180.0)).toFloat()
        moveTo(sk, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width - sk, size.height)
        lineTo(0f, size.height)
        close()
    }

class StarburstShape(private val spikes: Int = 12, private val innerRatio: Float = 0.66f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(cx, cy)
        val inner = outer * innerRatio
        val step = (PI / spikes).toFloat()
        var angle = (-PI / 2.0).toFloat()
        val path = Path()
        for (i in 0 until spikes * 2) {
            val r = if (i % 2 == 0) outer else inner
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            angle += step
        }
        path.close()
        return Outline.Generic(path)
    }
}
