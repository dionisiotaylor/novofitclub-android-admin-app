package com.novofitclub.dashboard.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.feature.coach.evaluations.EvaluationsScreen
import com.novofitclub.dashboard.feature.settings.SettingsScreen

private enum class CoachTab(val label: String, val icon: ImageVector) {
    Today("Hoy", Icons.Outlined.Home),
    Schedule("Horario", Icons.Outlined.CalendarMonth),
    Programs("Programas", Icons.Outlined.FitnessCenter),
    Evaluations("Evaluaciones", Icons.Outlined.MonitorHeart),
    Settings("Ajustes", Icons.Outlined.Settings),
}

/**
 * Novo Staff for coaches: Hoy, Horario, Programas (with the routine editor),
 * Evaluaciones and Ajustes. [viewing] is set when an admin looks at the app as a coach.
 */
@Composable
fun CoachRoot(app: NovoFitClubApp, viewing: ViewedCoach?) {
    var selected by rememberSaveable(viewing?.id) { mutableStateOf(CoachTab.Today) }
    val pendingOpen by app.pendingOpen.collectAsState()

    // A tapped Horario reminder or cita change opens Horario.
    LaunchedEffect(pendingOpen) {
        if (pendingOpen == "schedule") {
            selected = CoachTab.Schedule
            app.pendingOpen.value = null
        }
    }

    Scaffold(
        containerColor = NovoColors.background,
        topBar = {
            if (viewing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(NovoColors.panel).statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(hexColor(viewing.color) ?: NovoColors.statusNeutral))
                    Spacer(Modifier.width(8.dp))
                    Text("Viendo como ${viewing.name}", style = NovoFonts.semibold(13), maxLines = 1, modifier = Modifier.weight(1f))
                    TextButton(onClick = { app.auth.viewingCoach.value = null }) {
                        Text("Salir", style = NovoFonts.semibold(13).copy(color = NovoColors.accent))
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = NovoColors.panel) {
                CoachTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NovoColors.accent,
                            selectedTextColor = NovoColors.accent,
                            indicatorColor = NovoColors.accent.copy(alpha = 0.15f),
                            unselectedIconColor = NovoColors.textSecondary,
                            unselectedTextColor = NovoColors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(NovoColors.background).padding(padding)) {
            when (selected) {
                CoachTab.Today -> CoachTodayScreen(app, viewing, showEvaluations = { selected = CoachTab.Evaluations }) { selected = CoachTab.Programs }
                CoachTab.Schedule -> CoachScheduleScreen(app, viewing)
                CoachTab.Programs -> CoachProgramsScreen(app, viewing)
                CoachTab.Evaluations -> EvaluationsScreen(app, viewing)
                CoachTab.Settings -> SettingsScreen(app.auth, app.appLock, app.api) {
                    app.push.reset()
                    app.auth.logout()
                }
            }
        }
    }
}

/** A small tinted capsule: status, deadline, kind of cita… */
@Composable
fun CoachChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NovoFonts.semibold(11).copy(color = color),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
