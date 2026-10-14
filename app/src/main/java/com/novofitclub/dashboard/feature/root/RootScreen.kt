package com.novofitclub.dashboard.feature.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.feature.settings.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Sales("Ventas", Icons.Outlined.ShoppingCart),
    Members("Miembros", Icons.Outlined.People),
    Cash("Caja", Icons.Outlined.Payments),
    Settings("Ajustes", Icons.Outlined.Settings),
}

/** Signed-in shell: the same four tabs as the iOS app. */
@Composable
fun RootScreen(app: NovoFitClubApp) {
    var selected by rememberSaveable { mutableStateOf(Tab.Sales) }

    Scaffold(
        containerColor = NovoColors.background,
        bottomBar = {
            NavigationBar(containerColor = NovoColors.panel) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
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
                Tab.Sales -> ComingSoon("Ventas")
                Tab.Members -> ComingSoon("Miembros")
                Tab.Cash -> ComingSoon("Caja")
                Tab.Settings -> SettingsScreen(app.auth, app.appLock)
            }
        }
    }
}

// Placeholder until each tab is ported from the iOS app.
@Composable
private fun ComingSoon(title: String) {
    Column {
        SectionHeader(title = title)
        Text(
            "Pantalla en construcción.",
            style = NovoFonts.body(14).copy(color = NovoColors.textSecondary),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
