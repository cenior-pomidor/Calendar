package io.github.ceniorpomidor.workcalendar.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import io.github.ceniorpomidor.workcalendar.R
import kotlin.random.Random

internal val LocalSketch = staticCompositionLocalOf { false }

internal object Sketch {
    val paper = Color(0xFFF6F1E7)
    val sheet = Color(0xFFFFFCF6)
    val ink = Color(0xFF141414)
    val graphite = Color(0xFF4A4A4A)
    val violet = Color(0xFF6D28D9)
    val blue = Color(0xFF1D4ED8)
    val lime = Color(0xFFC8FF2E)
    val red = Color(0xFFE63946)
    val pink = Color(0xFFEC4899)
    val orange = Color(0xFFF97316)
    val hand = FontFamily(Font(R.font.hand), Font(R.font.hand_bold, FontWeight.Bold))
    val tag = FontFamily(Font(R.font.spray))
    val colors = lightColorScheme(primary = violet, onPrimary = Color.White, secondary = blue,
        background = paper, onBackground = ink, surface = paper, onSurface = ink,
        surfaceContainer = sheet, surfaceContainerLow = sheet, surfaceContainerHigh = Color(0xFFECE8DE),
        surfaceContainerHighest = Color(0xFFE6E0D5), onSurfaceVariant = graphite,
        secondaryContainer = lime, onSecondaryContainer = ink, outline = graphite)
    private val base = Typography()
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = hand),
        displayMedium = base.displayMedium.copy(fontFamily = hand),
        displaySmall = base.displaySmall.copy(fontFamily = hand),
        headlineLarge = base.headlineLarge.copy(fontFamily = hand),
        headlineMedium = base.headlineMedium.copy(fontFamily = hand),
        headlineSmall = base.headlineSmall.copy(fontFamily = hand),
        titleLarge = base.titleLarge.copy(fontFamily = hand),
        titleMedium = base.titleMedium.copy(fontFamily = hand),
        titleSmall = base.titleSmall.copy(fontFamily = hand),
        bodyLarge = base.bodyLarge.copy(fontFamily = hand),
        bodyMedium = base.bodyMedium.copy(fontFamily = hand),
        bodySmall = base.bodySmall.copy(fontFamily = hand),
        labelLarge = base.labelLarge.copy(fontFamily = hand),
        labelMedium = base.labelMedium.copy(fontFamily = hand),
        labelSmall = base.labelSmall.copy(fontFamily = hand),
    )
}

internal fun Modifier.paperGrain() = this.drawWithCache {
    val random = Random(911)
    val points = List((size.width * size.height / (9.dp.toPx() * 9.dp.toPx())).toInt()) {
        Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
    }
    onDrawBehind {
        drawRect(Sketch.paper)
        drawPoints(points, androidx.compose.ui.graphics.PointMode.Points, Sketch.ink.copy(alpha = .085f), .8.dp.toPx())
    }
}

internal fun Modifier.sticker(enabled: Boolean): Modifier {
    if (!enabled) return this
    val shape = RoundedCornerShape(8.dp)
    return drawBehind { drawRoundRect(Sketch.ink, Offset(2.dp.toPx(), 3.dp.toPx()), size, CornerRadius(8.dp.toPx())) }
        .border(1.5.dp, Sketch.ink, shape)
}
