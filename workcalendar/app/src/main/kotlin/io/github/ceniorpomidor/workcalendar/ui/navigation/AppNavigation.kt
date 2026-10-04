package io.github.ceniorpomidor.workcalendar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.ceniorpomidor.workcalendar.AppContainer
import io.github.ceniorpomidor.workcalendar.ui.theme.WorkCalendarTheme

@Composable
fun WorkCalendarRoot(container: AppContainer, pendingRoute: String?, onRouteHandled: () -> Unit) {
    WorkCalendarTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Рабочий календарь")
        }
    }
}
