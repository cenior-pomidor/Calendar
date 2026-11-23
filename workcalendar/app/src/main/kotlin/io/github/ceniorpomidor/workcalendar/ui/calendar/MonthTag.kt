package io.github.ceniorpomidor.workcalendar.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ceniorpomidor.workcalendar.ui.theme.AppIcons
import io.github.ceniorpomidor.workcalendar.ui.theme.Sketch
import io.github.ceniorpomidor.workcalendar.ui.theme.paperGrain
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun artworkId(name: String): Int {
    val context = LocalContext.current
    // The photos are optional build inputs. Resolve by name so debug builds also work without them.
    return remember(context, name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
}

@Composable
internal fun GraffitiNumber(number: String, digitHeight: Dp, tint: Color) {
    val ids = number.map { artworkId("graffiti_digit_$it") }
    if (ids.any { it == 0 }) {
        BasicText(
            number,
            style = androidx.compose.ui.text.TextStyle(
                color = tint, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(7.sp, digitHeight.value.sp, 0.5.sp),
            modifier = Modifier.height(digitHeight),
        )
        return
    }
    val digits = ids.map { painterResource(it) }
    val widths = digits.map { digitHeight * (it.intrinsicSize.width / it.intrinsicSize.height) }
    androidx.compose.foundation.layout.BoxWithConstraints {
        val spacing = 1.dp
        val naturalWidth = widths.fold(0.dp) { total, width -> total + width } - spacing * (digits.size - 1)
        val scale = (maxWidth / naturalWidth).coerceAtMost(1f)
        Row(horizontalArrangement = Arrangement.spacedBy(-spacing * scale), verticalAlignment = Alignment.CenterVertically) {
            digits.forEachIndexed { index, digit ->
                Image(digit, contentDescription = null, modifier = Modifier.size(widths[index] * scale, digitHeight * scale),
                    contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(tint))
            }
        }
    }
}

@Composable
internal fun SketchCalendarHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
    onHelp: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().paperGrain().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.height(44.dp).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                val year = month.year.toString()
                Box(Modifier.clearAndSetSemantics { contentDescription = year }) {
                    GraffitiNumber(year, 20.dp, Sketch.ink)
                }
            }
            Row {
                HeaderAction(AppIcons.Today, "Сегодня", onToday)
                HeaderAction(AppIcons.Search, "Поиск", onSearch)
                HeaderAction(AppIcons.Help, "Обозначения", onHelp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(AppIcons.ChevronLeft, "Предыдущий месяц", onPrevious)
            Box(Modifier.weight(1f).height(54.dp), contentAlignment = Alignment.Center) {
                HeaderDoodles(Modifier.matchParentSize())
                val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"))
                val id = artworkId("graffiti_month_" + month.monthValue.toString().padStart(2, '0'))
                if (id != 0) {
                    Image(painterResource(id), contentDescription = name,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp).height(50.dp),
                        contentScale = ContentScale.Fit)
                } else {
                    BasicText(name.uppercase(Locale.forLanguageTag("ru")), Modifier.padding(horizontal = 17.dp),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Sketch.ink, fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        ), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(12.sp, 32.sp))
                }
            }
            HeaderAction(AppIcons.ChevronRight, "Следующий месяц", onNext)
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Sketch.ink) }
}

@Composable
private fun HeaderDoodles(modifier: Modifier) {
    Canvas(modifier) {
        val ink = Sketch.violet.copy(alpha = .38f)
        val star = Offset(6.dp.toPx(), size.height * .29f)
        val stroke = 1.dp.toPx()
        for (delta in listOf(Offset(3.dp.toPx(), 0f), Offset(stroke, 4.dp.toPx()), Offset(-2.dp.toPx(), 3.dp.toPx()))) {
            drawLine(ink, star - delta, star + delta, stroke)
        }
        val end = Offset(size.width - 9.dp.toPx(), size.height * .72f)
        drawLine(Sketch.blue.copy(alpha = .33f), end + Offset(-5.dp.toPx(), 2.dp.toPx()), end, stroke)
        drawCircle(ink, stroke, end + Offset(3.dp.toPx(), -6.dp.toPx()))
    }
}
