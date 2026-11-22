package io.github.ceniorpomidor.workcalendar.ui.calendar

import io.github.ceniorpomidor.workcalendar.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ceniorpomidor.workcalendar.R
import io.github.ceniorpomidor.workcalendar.domain.model.AppTheme

private val classicFont = FontFamily(Font(R.font.calendar_inter))


@Immutable
internal data class CalendarStyle(
    val art: AppTheme,
    val background: Color,
    val backgroundEnd: Color,
    val paper: Color,
    val ink: Color,
    val accent: Color,
) {
    val wall get() = art == AppTheme.GRAFFITI
    val dark get() = paper.luminance() < 0.4f
    val rule get() = ink.copy(alpha = 0.13f)
    val secondaryInk get() = lerp(ink, paper, 0.18f)
    val holiday get() = if (wall) Sketch.blue else if (dark) Color(0xFFFFC8BC) else Color(0xFF853C36)
    val onAccent get() = if (dark) Color(0xFF15191D) else Color.White

    val titleStyle get() = TextStyle(
        fontFamily = if (wall) Sketch.tag else classicFont,
        fontSize = if (wall) 30.sp else 22.sp,
        letterSpacing = if (wall) 0.35.sp else 0.sp,
        shadow = if (wall) Shadow(accent.copy(alpha = 0.34f), Offset(1.6f, 2.4f), 0.2f) else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val weekdayStyle get() = titleStyle.copy(fontFamily = if (wall) Sketch.hand else classicFont, fontSize = if (wall) 15.sp else 11.sp, shadow = null)

    fun dateStyle(small: Boolean) = titleStyle.copy(
        fontSize = if (wall) { if (small) 18.sp else 26.sp } else { if (small) 12.sp else 16.sp },
        fontFamily = if (wall) Sketch.hand else classicFont,
        fontWeight = if (wall) androidx.compose.ui.text.font.FontWeight.Bold else null,
        fontFeatureSettings = "tnum",
        shadow = null,
    )
}

@Composable
internal fun calendarStyle(art: AppTheme): CalendarStyle {
    val scheme = MaterialTheme.colorScheme
    if (art == AppTheme.DEFAULT) {
        return CalendarStyle(art, scheme.surface, scheme.surfaceContainer, scheme.surface, scheme.onSurface, scheme.primary)
    }
    return CalendarStyle(art, Sketch.paper, Sketch.paper, Sketch.sheet, Sketch.ink, Sketch.violet)
}

internal fun Modifier.calendarBackdrop(style: CalendarStyle): Modifier =
    if (style.wall) paperGrain() else background(Brush.verticalGradient(listOf(style.background, style.backgroundEnd)))

internal fun Modifier.calendarSheet(style: CalendarStyle, textured: Boolean = false): Modifier {
    if (style.wall && textured) return this
    val shape = RoundedCornerShape(if (style.wall) 8.dp else 12.dp)
    return sticker(style.wall).clip(shape).background(style.paper).border(1.dp, style.rule, shape)
}

@Composable
internal fun CalendarDate(
    number: Int,
    style: CalendarStyle,
    today: Boolean,
    holiday: Boolean,
    small: Boolean,
    modifier: Modifier = Modifier,
    circled: Boolean = today,
    seed: Long = 0,
) {
    val type = style.dateStyle(small)
    Box(modifier, contentAlignment = Alignment.Center) {
        val badge = if (style.wall && circled) Modifier.drawBehind {
            val w = size.width
            val h = size.height
            val k = seed.mod(7) * .014f
            val loop = androidx.compose.ui.graphics.Path().apply {
                moveTo(.44f*w, h*.09f)
                cubicTo(w*(1+k), -h*.04f, w*1.10f, h*.87f, w*.57f, h*.92f)
                cubicTo(-w*.10f, h*1.03f, -w*.06f, h*.12f, w*.54f, h*.07f)
            }
            drawPath(loop, if (today) Sketch.red else Sketch.violet, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
        }.padding(horizontal = 5.dp) else if (today) Modifier.background(style.accent, CircleShape).padding(horizontal = 4.dp) else Modifier
        BasicText(
            number.toString(),
            modifier = badge,
            style = type.copy(color = when {
                today && !style.wall -> style.onAccent
                holiday -> style.holiday
                else -> style.ink
            }),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(8.sp, type.fontSize, 1.sp),
        )
    }
}
