package com.novofitclub.dashboard.feature.cash

import androidx.compose.runtime.Composable
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.SectionHeader

/** Root of the Caja tab. */
@Composable
fun CashTab(app: NovoFitClubApp) {
    SectionHeader(title = "Caja")
    EmptyState("Pantalla en construcción.")
}
