package io.github.ceniorpomidor.workcalendar.ui.calendar

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import io.github.ceniorpomidor.workcalendar.R
import io.github.ceniorpomidor.workcalendar.ui.theme.Sketch

@Composable
internal fun MonthTag(title: String, month: Int) {
    val context = LocalContext.current
    val paint = remember(context) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.spray)
            strokeJoin = Paint.Join.ROUND
        }
    }
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = title }) {
        val inset = 3.dp.toPx()
        paint.textSize = 32.sp.toPx()
        paint.textSize *= minOf(1f, (size.width - 2*inset).coerceAtLeast(0f) / paint.measureText(title),
            (size.height - 2*inset) / (paint.descent() - paint.ascent()))
        val y = (size.height - paint.descent() - paint.ascent()) / 2
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6.dp.toPx()
        paint.color = Sketch.ink.toArgb()
        drawContext.canvas.nativeCanvas.drawText(title, inset, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = when (month % 3) { 0 -> Sketch.orange; 1 -> Sketch.lime; else -> Sketch.pink }.toArgb()
        drawContext.canvas.nativeCanvas.drawText(title, inset, y, paint)
        drawCircle(Sketch.violet, 1.5.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width-inset, y+inset))
    }
}
