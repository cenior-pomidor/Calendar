package io.github.ceniorpomidor.workcalendar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ceniorpomidor.workcalendar.AppContainer
import io.github.ceniorpomidor.workcalendar.domain.model.AbsenceType
import io.github.ceniorpomidor.workcalendar.domain.model.AppSettings
import io.github.ceniorpomidor.workcalendar.domain.model.NavItem
import io.github.ceniorpomidor.workcalendar.domain.model.NavLabels
import io.github.ceniorpomidor.workcalendar.ui.absence.AbsenceEditScreen
import io.github.ceniorpomidor.workcalendar.ui.calendar.CalendarScreen
import io.github.ceniorpomidor.workcalendar.ui.components.LocalCurrency
import io.github.ceniorpomidor.workcalendar.ui.components.LocalSnackbar
import io.github.ceniorpomidor.workcalendar.ui.finance.AccrualEditScreen
import io.github.ceniorpomidor.workcalendar.ui.finance.FinanceScreen
import io.github.ceniorpomidor.workcalendar.ui.finance.HistoryScreen
import io.github.ceniorpomidor.workcalendar.ui.finance.PaymentEditScreen
import io.github.ceniorpomidor.workcalendar.ui.finance.StatsScreen
import io.github.ceniorpomidor.workcalendar.ui.lock.FinanceLockGate
import io.github.ceniorpomidor.workcalendar.ui.onboarding.OnboardingScreen
import io.github.ceniorpomidor.workcalendar.ui.search.SearchScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.AbsenceRulesScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.AlarmScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.AppearanceScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.ApplyScheduleScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.BackupScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.ChangeLogScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.HolidaysScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.NotificationSettingsScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.PayoutRuleEditScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.PayoutRulesScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.ProfileScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.RatesScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.SecurityScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.SettingsScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.TemplateEditScreen
import io.github.ceniorpomidor.workcalendar.ui.settings.TemplatesScreen
import io.github.ceniorpomidor.workcalendar.ui.shift.ConfirmShiftScreen
import io.github.ceniorpomidor.workcalendar.ui.shift.ShiftEditScreen
import io.github.ceniorpomidor.workcalendar.ui.shift.UnconfirmedScreen
import io.github.ceniorpomidor.workcalendar.ui.theme.WorkCalendarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Bottom bar items chosen in the settings. */
private fun barItems(settings: AppSettings?): List<NavItem> = NavItem.normalize(settings?.appearance?.navItems ?: NavItem.DEFAULT)

/** Back action for a screen that can also be an item of the bottom bar (then it has no back button). */
private fun backFor(nav: NavHostController, settings: State<AppSettings?>, route: String): (() -> Unit)? {
    if (barItems(settings.value).any { it.info.route == route }) return null
    return { nav.popBackStack() }
}

private fun String?.toDateOrNull(): LocalDate? = this?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

@Composable
fun WorkCalendarRoot(container: AppContainer, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val settingsState = container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settingsState.value ?: AppSettings()
    WorkCalendarTheme(themeMode = current.theme, dynamicColor = current.dynamicColor, appearance = current.appearance) {
        val snackbar = remember { SnackbarHostState() }
        CompositionLocalProvider(LocalSnackbar provides snackbar, LocalCurrency provides current.currency) {
            if (settingsState.value != null) {
                AppNavHost(container, settingsState, pendingRoute, onRouteHandled)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

private const val CALENDAR_ROUTE = "calendar?date={date}"

@Composable
private fun AppNavHost(container: AppContainer, settingsState: State<AppSettings?>, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    // Settings are read inside destinations through the state object: capturing the value in the
    // graph builder would rebuild the graph (and reset navigation) on every settings change.
    val startDestination = remember { if (settingsState.value?.onboardingDone == true) CALENDAR_ROUTE else "onboarding" }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')
    val navItems = barItems(settingsState.value)
    val navLabels = settingsState.value?.appearance?.navLabels ?: NavLabels.ALWAYS
    val showBottomBar = navItems.any { it.info.route == currentRoute }
    val unconfirmed by remember(container) {
        flow {
            while (true) {
                emit(container.clock.now())
                delay(60_000)
            }
        }.flatMapLatest { now -> container.shifts.observeUnconfirmed(now) }.map { it.size }
    }.collectAsStateWithLifecycle(initialValue = 0)

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        openDeepLink(nav, route)
        onRouteHandled()
    }

    Column(Modifier.fillMaxSize()) {
        // The bottom bar handles the navigation bar inset itself.
        Box(Modifier.weight(1f).then(if (showBottomBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            NavHost(navController = nav, startDestination = startDestination) {
                composable("onboarding") {
                    OnboardingScreen(container, onDone = {
                        nav.navigate("calendar?date=") { popUpTo(nav.graph.id) { inclusive = true } }
                    })
                }
                composable(
                    CALENDAR_ROUTE,
                    arguments = listOf(
                        navArgument("date") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    CalendarScreen(
                        container = container,
                        initialDate = entry.arguments?.getString("date").toDateOrNull(),
                        onConfirmShift = { nav.navigate("confirm/$it") },
                        onEditShift = { nav.navigate("shift/edit/$it") },
                        onNewShift = { date, extra -> nav.navigate("shift/new?date=$date&extra=$extra") },
                        onNewAbsence = { type, date -> nav.navigate("absence/new?type=${type.name}&start=$date") },
                        onOpenAbsence = { nav.navigate("absence/$it") },
                        onOpenUnconfirmed = { nav.navigate("unconfirmed") },
                        onOpenFinance = { navigateTopLevel(nav, "finance") },
                        onSearch = { nav.navigate("search") },
                        onSetupSchedule = { nav.navigate("settings/templates") },
                    )
                }
                composable(
                    "shift/new?date={date}&extra={extra}",
                    arguments = listOf(
                        navArgument("date") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("extra") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    ShiftEditScreen(
                        container = container,
                        shiftId = null,
                        date = entry.arguments?.getString("date").toDateOrNull() ?: container.clock.today(),
                        extra = entry.arguments?.getBoolean("extra") ?: false,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("shift/edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    ShiftEditScreen(
                        container = container,
                        shiftId = entry.arguments?.getLong("id"),
                        date = container.clock.today(),
                        extra = false,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("confirm/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    ConfirmShiftScreen(
                        container = container,
                        shiftId = entry.arguments?.getLong("id") ?: 0L,
                        onEdit = { nav.navigate("shift/edit/$it") },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("unconfirmed") {
                    UnconfirmedScreen(container, onOpen = { nav.navigate("confirm/$it") }, onBack = backFor(nav, settingsState, "unconfirmed"))
                }
                composable(
                    "absence/new?type={type}&start={start}",
                    arguments = listOf(
                        navArgument("type") {
                            type = NavType.StringType
                            defaultValue = AbsenceType.VACATION.name
                        },
                        navArgument("start") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val type = runCatching { AbsenceType.valueOf(entry.arguments?.getString("type") ?: "") }.getOrDefault(AbsenceType.VACATION)
                    AbsenceEditScreen(
                        container = container,
                        absenceId = null,
                        type = type,
                        start = entry.arguments?.getString("start").toDateOrNull() ?: container.clock.today(),
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("absence/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    AbsenceEditScreen(
                        container = container,
                        absenceId = entry.arguments?.getLong("id"),
                        type = AbsenceType.VACATION,
                        start = container.clock.today(),
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("finance") {
                    FinanceLockGate(container, settingsState.value ?: AppSettings()) {
                        FinanceScreen(
                            container = container,
                            onBack = backFor(nav, settingsState, "finance"),
                            onHistory = { nav.navigate("finance/history") },
                            onStats = { nav.navigate("finance/stats") },
                            onPayout = { key -> nav.navigate("payment/new?key=$key") },
                            onPayment = { id -> nav.navigate("payment/$id") },
                            onNewPayment = { nav.navigate("payment/new?key=") },
                            onAccrual = { id -> nav.navigate("accrual/$id") },
                            onNewAccrual = { nav.navigate("accrual/0") },
                            onOpenAbsence = { nav.navigate("absence/$it") },
                            onOpenUnconfirmed = { nav.navigate("unconfirmed") },
                            onRates = { nav.navigate("settings/rates") },
                            onPayoutRules = { nav.navigate("settings/payouts") },
                        )
                    }
                }
                composable("finance/history") {
                    FinanceLockGate(container, settingsState.value ?: AppSettings()) {
                        HistoryScreen(
                            container = container,
                            onBack = backFor(nav, settingsState, "finance/history"),
                            onShift = { nav.navigate("confirm/$it") },
                            onPayment = { nav.navigate("payment/$it") },
                            onAccrual = { nav.navigate("accrual/$it") },
                            onAbsence = { nav.navigate("absence/$it") },
                        )
                    }
                }
                composable("finance/stats") {
                    FinanceLockGate(container, settingsState.value ?: AppSettings()) { StatsScreen(container, onBack = backFor(nav, settingsState, "finance/stats")) }
                }
                composable(
                    "payment/new?key={key}",
                    arguments = listOf(
                        navArgument("key") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    PaymentEditScreen(container, paymentId = null, payoutKey = entry.arguments?.getString("key").orEmpty(), onDone = { nav.popBackStack() })
                }
                composable("payment/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    PaymentEditScreen(container, paymentId = entry.arguments?.getLong("id"), payoutKey = "", onDone = { nav.popBackStack() })
                }
                composable("accrual/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    AccrualEditScreen(container, accrualId = entry.arguments?.getLong("id")?.takeIf { it > 0 }, onDone = { nav.popBackStack() })
                }
                composable("search") {
                    SearchScreen(
                        container = container,
                        onBack = backFor(nav, settingsState, "search"),
                        onShift = { nav.navigate("confirm/$it") },
                        onDate = { nav.navigate("calendar?date=$it") },
                        onAbsence = { nav.navigate("absence/$it") },
                        onPayment = { nav.navigate("payment/$it") },
                        onAccrual = { nav.navigate("accrual/$it") },
                    )
                }
                composable("settings") {
                    SettingsScreen(container, settingsState.value ?: AppSettings(), onNavigate = { nav.navigate(it) })
                }
                composable("settings/profile") { ProfileScreen(container, settingsState.value ?: AppSettings(), onBack = { nav.popBackStack() }) }
                composable("settings/templates") {
                    TemplatesScreen(
                        container = container,
                        onBack = backFor(nav, settingsState, "settings/templates"),
                        onEdit = { nav.navigate("settings/template/$it") },
                        onApply = { nav.navigate("settings/apply/$it") },
                    )
                }
                composable("settings/template/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TemplateEditScreen(
                        container = container,
                        templateId = entry.arguments?.getLong("id")?.takeIf { it > 0 },
                        onBack = { nav.popBackStack() },
                        onSavedApply = { id -> nav.navigate("settings/apply/$id") { popUpTo("settings/templates") } },
                    )
                }
                composable("settings/apply/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    ApplyScheduleScreen(container, templateId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
                }
                composable("settings/rates") { RatesScreen(container, onBack = { nav.popBackStack() }) }
                composable("settings/payouts") {
                    PayoutRulesScreen(container, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("settings/payout/$it") })
                }
                composable("settings/payout/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    PayoutRuleEditScreen(container, ruleId = entry.arguments?.getLong("id")?.takeIf { it > 0 }, onBack = { nav.popBackStack() })
                }
                composable("settings/absence") { AbsenceRulesScreen(container, settingsState.value ?: AppSettings(), onBack = { nav.popBackStack() }) }
                composable("settings/notifications") { NotificationSettingsScreen(container, settingsState.value ?: AppSettings(), onBack = { nav.popBackStack() }) }
                composable("settings/display") { AppearanceScreen(container, settingsState.value ?: AppSettings(), onBack = { nav.popBackStack() }) }
                composable("alarm") {
                    AlarmScreen(container, settingsState.value ?: AppSettings(), onBack = backFor(nav, settingsState, "alarm"))
                }
                composable("settings/holidays") { HolidaysScreen(container, settingsState.value ?: AppSettings(), onBack = { nav.popBackStack() }) }
                composable("settings/backup") { BackupScreen(container, onBack = { nav.popBackStack() }) }
                composable("settings/security") { SecurityScreen(container, settingsState.value ?: AppSettings(), onBack = { nav.popBackStack() }) }
                composable("settings/log") { ChangeLogScreen(container, onBack = { nav.popBackStack() }) }
            }
        }
        if (showBottomBar) {
            NavigationBar {
                for (item in navItems) {
                    val info = item.info
                    val selected = currentRoute == info.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTopLevel(nav, info.route) },
                        icon = {
                            BadgedBox(badge = { if (item == NavItem.UNCONFIRMED && unconfirmed > 0) Badge { Text(unconfirmed.toString()) } }) {
                                Icon(if (selected) info.selectedIcon else info.icon, contentDescription = if (navLabels == NavLabels.NEVER) info.label else null)
                            }
                        },
                        label = if (navLabels == NavLabels.NEVER) null else ({ Text(info.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }),
                        alwaysShowLabel = navLabels == NavLabels.ALWAYS,
                    )
                }
            }
        }
    }
}

private fun navigateTopLevel(nav: NavHostController, route: String) {
    val target = if (route == "calendar") "calendar?date=" else route
    nav.navigate(target) {
        popUpTo(CALENDAR_ROUTE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Routes coming from notifications and the widget. */
private fun openDeepLink(nav: NavHostController, route: String) {
    when {
        route == "calendar" -> navigateTopLevel(nav, "calendar")
        route == "finance" -> navigateTopLevel(nav, "finance")
        route.startsWith("day/") -> nav.navigate("calendar?date=${route.removePrefix("day/")}") { launchSingleTop = true }
        else -> runCatching { nav.navigate(route) }
    }
}
