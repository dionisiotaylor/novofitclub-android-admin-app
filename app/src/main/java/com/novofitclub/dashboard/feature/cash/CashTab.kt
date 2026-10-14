package com.novofitclub.dashboard.feature.cash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoDimens
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.feature.cash.common.RecordDetailScreen
import org.json.JSONObject

/**
 * Screens pushed on top of the Caja hub. Encoded as strings so the back
 * stack survives tab switches and configuration changes (rememberSaveable).
 */
private sealed interface CashRoute {
    data object CajaChica : CashRoute
    data object Sobres : CashRoute
    data object Cierres : CashRoute
    data class MovementDetail(val recordJson: String) : CashRoute

    fun encode(): String = when (this) {
        CajaChica -> "caja"
        Sobres -> "sobres"
        Cierres -> "cierres"
        is MovementDetail -> "detail:$recordJson"
    }

    companion object {
        fun decode(raw: String): CashRoute? = when {
            raw == "caja" -> CajaChica
            raw == "sobres" -> Sobres
            raw == "cierres" -> Cierres
            raw.startsWith("detail:") -> MovementDetail(raw.removePrefix("detail:"))
            else -> null
        }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<CashRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<CashRoute>().apply { addAll(saved.mapNotNull(CashRoute::decode)) } },
)

/**
 * Root of the Caja tab — a landing hub with three separate financial views
 * (Caja Chica, Sobres, Cierres), each pushed as its own screen, same as the
 * iOS `CashTabView` NavigationStack.
 */
@Composable
fun CashTab(app: NovoFitClubApp) {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<CashRoute>() }
    val push: (CashRoute) -> Unit = { backStack.add(it) }
    val pop: () -> Unit = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.isNotEmpty(), onBack = pop)

    // Activity-scoped, keyed per section so data survives pushing a detail
    // screen and switching tabs (iOS keeps the NavigationStack alive too).
    val cashViewModel = viewModel(key = "cash.cajaChica") { CashViewModel(app.api) }
    val sobresViewModel = viewModel(key = "cash.sobres") { SobresViewModel(app.api) }
    val cierresViewModel = viewModel(key = "cash.cierres") { CierresViewModel(app.api) }

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        when (val top = backStack.lastOrNull()) {
            null -> CashHub(onOpen = { route ->
                when (route) {
                    CashRoute.CajaChica -> cashViewModel.reset()
                    CashRoute.Sobres -> sobresViewModel.reset()
                    CashRoute.Cierres -> cierresViewModel.reset()
                    is CashRoute.MovementDetail -> Unit
                }
                push(route)
            })
            CashRoute.CajaChica -> CajaChicaScreen(
                viewModel = cashViewModel,
                onBack = pop,
                onOpenMovement = { push(CashRoute.MovementDetail(it.fields.toString())) },
            )
            CashRoute.Sobres -> SobresScreen(sobresViewModel, onBack = pop)
            CashRoute.Cierres -> CierresScreen(cierresViewModel, onBack = pop)
            is CashRoute.MovementDetail -> RecordDetailScreen(
                title = "Movimiento",
                record = runCatching { JSONObject(top.recordJson) }.getOrElse { JSONObject() },
                breadcrumb = "CAJA CHICA",
                onBack = pop,
            )
        }
    }
}

@Composable
private fun CashHub(onOpen: (CashRoute) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SectionHeader(title = "Caja", subtitle = "Caja chica, sobres y cierres de caja")
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                NavRow(
                    title = "Caja Chica",
                    subtitle = "Movimientos y resumen",
                    icon = Icons.Filled.Payments,
                    tint = NovoColors.statusActive,
                    onClick = { onOpen(CashRoute.CajaChica) },
                )
            }
            item {
                NavRow(
                    title = "Sobres",
                    subtitle = "Saldos por recepcionista",
                    icon = Icons.Filled.Email,
                    tint = NovoColors.statusNeutral,
                    onClick = { onOpen(CashRoute.Sobres) },
                )
            }
            item {
                NavRow(
                    title = "Cierres",
                    subtitle = "Cierres de caja registradora",
                    icon = Icons.Filled.Verified,
                    tint = NovoColors.accent,
                    onClick = { onOpen(CashRoute.Cierres) },
                )
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NovoDimens.cornerRadius))
            .clickable(onClick = onClick)
            .cardBackground()
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.15f)),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NovoFonts.semibold(16))
            Text(
                subtitle,
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NovoColors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}
