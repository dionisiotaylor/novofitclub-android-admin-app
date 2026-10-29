package com.novofitclub.dashboard.feature.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.feature.cash.CashTab
import com.novofitclub.dashboard.feature.coach.CoachRoot
import com.novofitclub.dashboard.feature.members.MembersTab
import com.novofitclub.dashboard.feature.reception.MyClosingsScreen
import com.novofitclub.dashboard.feature.reception.MySalesScreen
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.SaleDetailScreen
import com.novofitclub.dashboard.feature.sales.SalesTab
import com.novofitclub.dashboard.feature.settings.SettingsScreen
import com.novofitclub.dashboard.feature.staff.EmployeesTab
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Sales("Ventas", Icons.Outlined.ShoppingCart),
    MySales("Mis ventas", Icons.Outlined.ShoppingCart),
    Members("Miembros", Icons.Outlined.People),
    Employees("Empleados", Icons.Outlined.Badge),
    Cash("Caja", Icons.Outlined.Payments),
    MyClosings("Mis cierres", Icons.Outlined.Inventory2),
    Settings("Ajustes", Icons.Outlined.Settings),
}

private val ADMIN_TABS = listOf(Tab.Sales, Tab.Members, Tab.Employees, Tab.Cash, Tab.Settings)
private val RECEPTION_TABS = listOf(Tab.MySales, Tab.Members, Tab.MyClosings, Tab.Settings)

/**
 * Signed-in shell, tabs by role (`novo-staff/v1/me`), as the iOS app:
 * admins get Ventas, Miembros, Empleados, Caja (and can look at the app as a
 * coach); reception Mis ventas, Miembros, Mis cierres; coaches their own
 * tabs. Hiding a tab is only convenience — each endpoint checks the role
 * again on the server.
 */
@Composable
fun RootScreen(app: NovoFitClubApp) {
    val auth by app.auth.state.collectAsState()
    val viewing by app.auth.viewingCoach.collectAsState()
    when (auth.role) {
        "admin" -> if (viewing != null) CoachRoot(app, viewing) else AdminRoot(app)
        "receptionist" -> StaffTabs(app, RECEPTION_TABS)
        "coach" -> CoachRoot(app, null)
        null -> ProfileLoading(app, auth.profileError)
        else -> NoAccess(app)
    }
}

/** The admin tabs, plus the sale a tapped "Nueva venta" push asks for — shown once the app is unlocked. */
@Composable
private fun AdminRoot(app: NovoFitClubApp) {
    StaffTabs(app, ADMIN_TABS)
    val saleId by app.pendingSaleId.collectAsState()
    val unlocked by app.appLock.isUnlocked.collectAsState()
    val id = saleId
    if (id != null && unlocked) {
        BackHandler { app.pendingSaleId.value = null }
        OverlayLayer {
            SaleDetailScreen(
                app = app,
                saleId = id,
                onBack = { app.pendingSaleId.value = null },
                onChanged = {},
                onOpenMember = {},
            )
        }
    }
}

@Composable
private fun StaffTabs(app: NovoFitClubApp, tabs: List<Tab>) {
    var selected by rememberSaveable { mutableStateOf(tabs.first()) }
    if (selected !in tabs) selected = tabs.first()

    Scaffold(
        containerColor = NovoColors.background,
        bottomBar = {
            NavigationBar(containerColor = NovoColors.panel) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1) },
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
                Tab.Sales -> SalesTab(app)
                Tab.MySales -> MySalesScreen(app)
                Tab.Members -> MembersTab(app)
                Tab.Employees -> EmployeesTab(app)
                Tab.Cash -> CashTab(app)
                Tab.MyClosings -> MyClosingsScreen(app)
                Tab.Settings -> SettingsScreen(app.auth, app.appLock, app.api) {
                    app.push.reset()
                    app.auth.logout()
                }
            }
        }
    }
}

/** A user without a staff role: nothing to show but Ajustes (to sign out). */
@Composable
private fun NoAccess(app: NovoFitClubApp) {
    Scaffold(containerColor = NovoColors.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SettingsScreen(app.auth, app.appLock, app.api) {
                app.push.reset()
                app.auth.logout()
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(28.dp))
                Text("Sin acceso", style = NovoFonts.semibold(16))
                Text(
                    "Tu usuario no tiene un rol de staff (administración, recepción o entrenador). Pedile a administración que te lo asigne.",
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Until `novo-staff/v1/me` answers the first time (later launches use the cached role), or with a retry when it can't be reached. */
@Composable
private fun ProfileLoading(app: NovoFitClubApp, error: String?) {
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(NovoColors.background), contentAlignment = Alignment.Center) {
        if (error == null) {
            CircularProgressIndicator(color = NovoColors.accent)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(32.dp))
                Text("No se pudo cargar tu perfil", style = NovoFonts.semibold(17))
                Text(error, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary), textAlign = TextAlign.Center)
                PrimaryButton("Reintentar", onClick = { scope.launch { app.auth.refreshProfile() } })
                SecondaryButton("Cerrar sesión", onClick = {
                    app.push.reset()
                    app.auth.logout()
                })
            }
        }
    }
}
