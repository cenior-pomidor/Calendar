package io.github.ceniorpomidor.workcalendar.ui.calendar

import io.github.ceniorpomidor.workcalendar.ui.theme.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.random.Random

internal enum class PaintReason { SHIFT, PAYOUT, NOTE, ALARM, HOLIDAY }
internal class DayPaint(val previous: CalendarMark?, val reason: PaintReason)

@Composable
internal fun sprayProgress(pass: DayPaint?, finished: (DayPaint) -> Unit): State<Float>? {
    if (pass == null) return null
    val progress = remember(pass) { Animatable(0f) }
    val onFinished = rememberUpdatedState(finished)
    LaunchedEffect(pass) {
        progress.animateTo(1f, tween(durationMillis = 620, easing = LinearEasing))
        onFinished.value(pass)
    }
    return progress.asState()
}

// Stable grains move with the spray head, including through the turn at the right edge.
private val mist = Random(32).let { random ->
    List(42) { Offset(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1) }
}

private fun DrawScope.marker(mark: CalendarMark?, style: CalendarStyle, seed: Long) {
    val w = size.width
    val h = size.height
    val skew = ((seed xor (seed ushr 7)).mod(9) - 4) * .006f * h
    fun stroke(color: Color, y: Float, width: Float) {
        val path = Path().apply {
            moveTo(.09f*w, y*h + skew)
            quadraticTo(.46f*w, y*h - skew - 2.dp.toPx(), .91f*w, y*h - skew)
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
    }
    when (mark) {
        null -> Unit
        CalendarMark.CONFIRMED -> stroke(Sketch.lime.copy(alpha = .82f), .28f, minOf(.26f*h, 17.dp.toPx()))
        CalendarMark.STARTED -> {
            stroke(Sketch.blue, .86f, 2.dp.toPx())
            stroke(Sketch.blue, .93f, 1.dp.toPx())
            val tip = Offset(.85f * w, .10f * h)
            drawLine(Sketch.blue, tip - Offset(4.dp.toPx(), -2.dp.toPx()), tip, 1.5.dp.toPx())
            drawLine(Sketch.blue, tip, tip + Offset(0f, 4.dp.toPx()), 1.5.dp.toPx())
        }
        CalendarMark.SHIFT -> stroke(Sketch.violet, .91f, 2.dp.toPx())
        else -> stroke(mark.fill(style), .29f, minOf(.27f*h, 18.dp.toPx()))
    }
}

@Composable
internal fun Modifier.dayPaint(
    style: CalendarStyle,
    mark: CalendarMark?,
    pass: DayPaint?,
    progress: State<Float>?,
    seed: Long = 0L,
): Modifier {
    val fill = mark?.fill(style) ?: Color.Transparent
    if (!style.wall) return background(fill)
    if (pass == null || progress == null) return drawBehind { marker(mark, style, seed) }
    val pigment = when (pass.reason) {
        PaintReason.SHIFT -> mark?.ink(style) ?: style.paper
        PaintReason.PAYOUT -> payoutInk(style)
        PaintReason.NOTE, PaintReason.ALARM -> style.accent
        PaintReason.HOLIDAY -> style.holiday
    }
    val wipe = remember(pass) { Path() }
    return drawBehind {
        val t = progress.value
        val swept = (t / 0.79f).coerceAtMost(1f)
        val x = size.width * (1f - abs(swept * 2f - 1f))
        val w = size.width
        val h = size.height
        // Adjacent swaths share a sloping seam. Each painted pixel belongs to exactly one pass.
        val seam = h * 0.40f
        val rise = h * 0.22f
        wipe.reset()
        wipe.moveTo(0f, 0f)
        if (swept < 0.5f) {
            wipe.lineTo(x, 0f)
            wipe.lineTo(x, seam + swept * 2 * rise)
            wipe.lineTo(0f, seam)
        } else {
            wipe.lineTo(w, 0f)
            wipe.lineTo(w, h)
            wipe.lineTo(x, h)
            wipe.lineTo(x, seam + (2 - 2 * swept) * rise)
            wipe.lineTo(0f, seam)
        }
        wipe.close()
        clipPath(wipe, ClipOp.Difference) { marker(pass.previous, style, seed) }
        clipPath(wipe) {
            marker(mark, style, seed)
            drawRect(pigment, alpha = 0.52f * (1 - t))
        }
        if (swept < 1f) {
            val headY = h * (0.15f + swept * 0.72f)
            for ((index, grain) in mist.withIndex()) {
                drawCircle(pigment.copy(alpha = 0.60f), (0.45f + index % 3 * 0.2f).dp.toPx(),
                    Offset(x + grain.x * w * 0.11f, headY + grain.y * h * 0.28f))
            }
        }
    }
}
