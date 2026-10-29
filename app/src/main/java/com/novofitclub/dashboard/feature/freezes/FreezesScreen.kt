package com.novofitclub.dashboard.feature.freezes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import com.novofitclub.dashboard.feature.sales.SearchField
import com.novofitclub.dashboard.feature.sales.StatusPill

/**
 * Congelamientos: the requests, by status, and "+" for a new one — the
 * wp-admin Congelamientos screen (congelados plugin). Receptionists create
 * requests and edit their own pending ones; administrators approve, deny,
 * void and archive them. Opened from Miembros' pill, not a tab of its own.
 * Host inside [ScopedViewModels] so each visit starts fresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezesScreen(app: NovoFitClubApp, onBack: () -> Unit) {
    val vm: FreezesViewModel = viewModel { FreezesViewModel(app.api) }
    val auth by app.auth.state.collectAsState()
    var opened by remember { mutableStateOf<Freeze?>(null) }
    var showingNew by remember { mutableStateOf(false) }

    BackHandler(enabled = opened == null && !showingNew, onBack = onBack)

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        PullToRefreshBox(isRefreshing = vm.isLoading && vm.items.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { SectionHeader("Congelamientos", "Solicitudes de congelar la membresía", breadcrumb = "MIEMBROS", onBack = onBack) }
                item { Tabs(vm) }
                item {
                    SearchField(
                        value = vm.search,
                        onValueChange = vm::updateSearch,
                        placeholder = "Buscar por miembro",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                val error = vm.errorMessage
                when {
                    vm.isLoading && vm.items.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NovoColors.accent)
                        }
                    }
                    error != null -> item { ErrorState(error, onRetry = { vm.load() }) }
                    vm.items.isEmpty() -> item { EmptyState("No hay solicitudes en esta vista.") }
                    else -> {
                        items(vm.items, key = { it.id }) { freeze ->
                            if (freeze.id == vm.items.last().id) LaunchedEffect(freeze.id) { vm.loadMore() }
                            FreezeRow(freeze, Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).clickable { opened = freeze })
                        }
                        if (vm.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (auth.can.requestFreezes) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .shadow(12.dp, CircleShape, ambientColor = NovoColors.accent, spotColor = NovoColors.accent)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(NovoColors.accent)
                    .clickable { showingNew = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva solicitud", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        opened?.let { freeze ->
            OverlayLayer {
                ScopedViewModels {
                    FreezeDetailScreen(app, freeze, onBack = { opened = null }, onChanged = { vm.load() })
                }
            }
        }
        if (showingNew) {
            OverlayLayer {
                ScopedViewModels {
                    FreezeFormScreen(
                        app = app,
                        freeze = null,
                        onDismiss = { showingNew = false },
                        onSaved = {
                            vm.selectTab(FreezesTab.Status(FreezeStatus.Pendiente))
                            vm.load()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Tabs(vm: FreezesViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        FreezesTab.all.forEach { tab ->
            val selected = vm.tab == tab
            Text(
                tab.title,
                style = NovoFonts.semibold(13).copy(color = if (selected) Color.White else NovoColors.textSecondary),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) NovoColors.accent else NovoColors.card)
                    .clickable { vm.selectTab(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun FreezeStatusBadge(status: FreezeStatus) {
    StatusPill(status.label, status.color, horizontal = 8.dp, vertical = 3.dp)
}

@Composable
private fun FreezeRow(freeze: Freeze, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().cardBackground().padding(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)),
        ) {
            Icon(freeze.type?.icon ?: Icons.Outlined.PauseCircle, contentDescription = null, tint = NovoColors.textPrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(freeze.memberName, style = NovoFonts.semibold(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${freeze.type?.title ?: freeze.typeLabel} · ${freeze.daysLabel}", style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val meta = NovoFonts.body(12).copy(color = NovoColors.textSecondary)
                Text(freeze.requesterName, style = meta)
                freeze.date?.let { Text("· ${Formatters.shortDate(it)}", style = meta) }
                if (freeze.evidence.isNotEmpty()) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(13.dp))
                    Text("${freeze.evidence.size}", style = meta)
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        FreezeStatusBadge(freeze.status)
    }
}

/**
 * Miembros' way into Congelamientos: a pill with how many requests are
 * waiting for a decision, refreshed each time Miembros comes back on screen.
 */
@Composable
fun FreezesEntryPill(app: NovoFitClubApp, onClick: () -> Unit) {
    var pending by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    LaunchedEffect(Unit) { pending = runCatching { FreezesViewModel.pendingCount(app.api) }.getOrNull() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(NovoColors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(Icons.Outlined.PauseCircle, contentDescription = null, tint = NovoColors.textPrimary, modifier = Modifier.size(16.dp))
        Text("Congelados", style = NovoFonts.semibold(13))
        val count = pending
        if (count != null && count.first > 0) {
            Text(
                if (count.second) "${count.first}+" else "${count.first}",
                style = NovoFonts.semibold(11).copy(color = Color.White),
                modifier = Modifier.clip(CircleShape).background(NovoColors.accent).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}
