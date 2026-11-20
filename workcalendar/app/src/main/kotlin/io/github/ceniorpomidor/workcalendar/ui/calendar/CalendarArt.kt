package io.github.ceniorpomidor.workcalendar.ui.calendar

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ceniorpomidor.workcalendar.R
import io.github.ceniorpomidor.workcalendar.domain.model.CalendarArt

private val classicFont = FontFamily(Font(R.font.calendar_inter))
private val wallFont = FontFamily(Font(R.font.calendar_wall))

@Immutable
internal data class CalendarStyle(
    val art: CalendarArt,
    val background: Color,
    val backgroundEnd: Color,
    val paper: Color,
    val ink: Color,
    val accent: Color,
) {
    val wall get() = art == CalendarArt.GRAFFITI
    val dark get() = paper.luminance() < 0.4f
    val rule get() = ink.copy(alpha = 0.13f)
    val secondaryInk get() = lerp(ink, paper, 0.18f)
    val holiday get() = if (dark) Color(0xFFFFC8BC) else Color(0xFF853C36)
    val onAccent get() = if (dark) Color(0xFF15191D) else Color.White

    val titleStyle get() = TextStyle(
        fontFamily = if (wall) wallFont else classicFont,
        fontSize = if (wall) 30.sp else 22.sp,
        letterSpacing = if (wall) 0.35.sp else 0.sp,
        shadow = if (wall) Shadow(accent.copy(alpha = 0.34f), Offset(1.6f, 2.4f), 0.2f) else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val weekdayStyle get() = titleStyle.copy(fontSize = if (wall) 15.sp else 11.sp, shadow = null)

    fun dateStyle(small: Boolean) = titleStyle.copy(
        fontSize = if (wall) { if (small) 18.sp else 26.sp } else { if (small) 12.sp else 16.sp },
        fontFeatureSettings = "tnum",
        shadow = null,
    )
}

@Composable
internal fun calendarStyle(art: CalendarArt): CalendarStyle {
    val scheme = MaterialTheme.colorScheme
    if (art == CalendarArt.CLASSIC) {
        return CalendarStyle(art, scheme.surface, scheme.surfaceContainer, scheme.surface, scheme.onSurface, scheme.primary)
    }
    return if (scheme.surface.luminance() < 0.4f) {
        CalendarStyle(art, Color(0xFF303944), Color(0xFF29313B), Color(0xFF29313B), Color(0xFFF6EAD5), Color(0xFFFFBC8B))
    } else {
        CalendarStyle(art, Color(0xFFE7D9C7), Color(0xFFDDD0BF), Color(0xFFF4EADE), Color(0xFF192E40), Color(0xFF974D31))
    }
}

@Composable
internal fun Modifier.calendarBackdrop(style: CalendarStyle): Modifier {
    val base = background(Brush.verticalGradient(listOf(style.background, style.backgroundEnd)))
    if (!style.wall) return base
    val texture = ImageBitmap.imageResource(R.drawable.calendar_masonry)
    return base.drawWithCache {
        val scale = 294.dp.toPx() / texture.width
        val shader = BitmapShader(texture.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shader.setLocalMatrix(Matrix().apply { setScale(scale, scale) })
        val wall = ShaderBrush(shader)
        onDrawBehind { drawRect(wall, alpha = if (style.dark) 0.78f else 0.64f) }
    }
}

internal fun Modifier.calendarSheet(style: CalendarStyle, textured: Boolean = false): Modifier {
    if (style.wall && textured) return this
    val shape = RoundedCornerShape(if (style.wall) 8.dp else 12.dp)
    return clip(shape).background(style.paper).border(1.dp, style.rule, shape)
}

@Composable
internal fun CalendarDate(
    number: Int,
    style: CalendarStyle,
    today: Boolean,
    holiday: Boolean,
    small: Boolean,
    modifier: Modifier = Modifier,
) {
    val type = style.dateStyle(small)
    Box(modifier, contentAlignment = Alignment.Center) {
        val badge = if (today) Modifier.background(style.accent, CircleShape).padding(horizontal = 4.dp) else Modifier
        BasicText(
            number.toString(),
            modifier = badge,
            style = type.copy(color = when {
                today -> style.onAccent
                holiday -> style.holiday
                else -> style.ink
            }),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(8.sp, type.fontSize, 1.sp),
        )
    }
}
