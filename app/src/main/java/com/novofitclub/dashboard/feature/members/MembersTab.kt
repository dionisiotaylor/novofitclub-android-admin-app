package com.novofitclub.dashboard.feature.members

import androidx.compose.runtime.Composable
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.SectionHeader

/** Root of the Miembros tab. */
@Composable
fun MembersTab(app: NovoFitClubApp) {
    SectionHeader(title = "Miembros")
    EmptyState("Pantalla en construcción.")
}
