package com.novofitclub.dashboard.feature.sales

import androidx.compose.runtime.Composable
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.SectionHeader

/** Root of the Ventas tab. */
@Composable
fun SalesTab(app: NovoFitClubApp) {
    SectionHeader(title = "Ventas")
    EmptyState("Pantalla en construcción.")
}
