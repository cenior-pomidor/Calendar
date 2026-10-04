package io.github.ceniorpomidor.workcalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.ceniorpomidor.workcalendar.domain.model.ThemeMode

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6E5676),
    tertiaryContainer = Color(0xFFF7D8FF),
    onTertiaryContainer = Color(0xFF27132F),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDABDE2),
    tertiaryContainer = Color(0xFF553F5D),
    onTertiaryContainer = Color(0xFFF7D8FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colors of day and shift states; the same in the calendar, lists, legend and widget. */
@Immutable
data class StatusPalette(
    val planned: Color,
    val onPlanned: Color,
    val inProgress: Color,
    val onInProgress: Color,
    val awaiting: Color,
    val onAwaiting: Color,
    val confirmed: Color,
    val onConfirmed: Color,
    val missed: Color,
    val onMissed: Color,
    val cancelled: Color,
    val onCancelled: Color,
    val extra: Color,
    val onExtra: Color,
    val vacation: Color,
    val onVacation: Color,
    val sick: Color,
    val onSick: Color,
    val otherAbsence: Color,
    val onOtherAbsence: Color,
    val holiday: Color,
    val positive: Color,
    val negative: Color,
)

val LightStatus = StatusPalette(
    planned = Color(0xFFDCE7FB),
    onPlanned = Color(0xFF1D4E9E),
    inProgress = Color(0xFFC9DBFF),
    onInProgress = Color(0xFF0E3C84),
    awaiting = Color(0xFFFFE3AE),
    onAwaiting = Color(0xFF6E4400),
    confirmed = Color(0xFFD2EFD9),
    onConfirmed = Color(0xFF1B5E2E),
    missed = Color(0xFFFAD4D0),
    onMissed = Color(0xFF8C1D18),
    cancelled = Color(0xFFE8E8EA),
    onCancelled = Color(0xFF5F6066),
    extra = Color(0xFFEADDFB),
    onExtra = Color(0xFF5A2A9C),
    vacation = Color(0xFFCDEEF2),
    onVacation = Color(0xFF0B5F67),
    sick = Color(0xFFF8D7E6),
    onSick = Color(0xFF8A1F4F),
    otherAbsence = Color(0xFFEDE1D4),
    onOtherAbsence = Color(0xFF5B4636),
    holiday = Color(0xFFD0453C),
    positive = Color(0xFF2E7D45),
    negative = Color(0xFFC62828),
)

val DarkStatus = StatusPalette(
    planned = Color(0xFF23395F),
    onPlanned = Color(0xFFC3D6FF),
    inProgress = Color(0xFF1D4479),
    onInProgress = Color(0xFFD8E5FF),
    awaiting = Color(0xFF4F3A10),
    onAwaiting = Color(0xFFFFD68A),
    confirmed = Color(0xFF1F4129),
    onConfirmed = Color(0xFFA8E3B6),
    missed = Color(0xFF5A211D),
    onMissed = Color(0xFFFFB4AB),
    cancelled = Color(0xFF34353A),
    onCancelled = Color(0xFFB9BAC0),
    extra = Color(0xFF3E2A5E),
    onExtra = Color(0xFFDCC7FF),
    vacation = Color(0xFF12474D),
    onVacation = Color(0xFFA2E7EE),
    sick = Color(0xFF4F2236),
    onSick = Color(0xFFFFB1D0),
    otherAbsence = Color(0xFF43382E),
    onOtherAbsence = Color(0xFFE5D3C2),
    holiday = Color(0xFFFF8A80),
    positive = Color(0xFF7BD893),
    negative = Color(0xFFFF8A80),
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatus }

object AppTheme {
    val status: StatusPalette
        @Composable get() = LocalStatusPalette.current
}

@Composable
fun WorkCalendarTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalStatusPalette provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
