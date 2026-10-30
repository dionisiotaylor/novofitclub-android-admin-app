package com.novofitclub.dashboard.feature.members

import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import com.novofitclub.dashboard.feature.freezes.FreezesScreen
import com.novofitclub.dashboard.feature.freezes.FreezesEntryPill
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoDimens
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.feature.sales.SaleDetailScreen
import com.novofitclub.dashboard.feature.sales.SearchField

/** Root of the Miembros tab: the member list plus an in-tab back stack. */
@Composable
fun MembersTab(app: NovoFitClubApp) {
    val vm: MembersViewModel = viewModel { MembersViewModel(app.api) { app.auth.state.value.can.editMemberDates } }

    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        when (val route = vm.stack.lastOrNull()) {
            null -> {
                val auth by app.auth.state.collectAsState()
                MembersListScreen(vm) {
                    if (auth.role == "admin" || auth.can.requestFreezes) FreezesEntryPill(app, onClick = vm::openFreezes)
                }
            }
            MembersRoute.Freezes -> ScopedViewModels { FreezesScreen(app, onBack = vm::pop) }
            is MembersRoute.Detail -> MemberDetailScreen(
                app = app,
                state = route.state,
                onBack = vm::pop,
                onEdit = { vm.openEdit(route.state) },
                onOpenSale = { vm.openSale(it, route.state) },
            )
            is MembersRoute.Edit -> MemberEditScreen(
                state = route.state,
                onCancel = vm::pop,
                onSaved = { profile ->
                    route.detail.applySaved(profile)
                    vm.popIfTop(route)
                },
            )
            is MembersRoute.Sale -> SaleDetailScreen(
                app = app,
                saleId = route.saleId,
                onBack = vm::pop,
                // Deleting or editing a sale can move the member's dates.
                onChanged = { route.detail.reload() },
                onOpenMember = vm::openMember,
            )
            is MembersRoute.Create -> MemberCreateScreen(
                state = route.state,
                vm = vm,
                onCancel = vm::pop,
                onDone = { vm.popIfTop(route) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersListScreen(vm: MembersViewModel, headerTrailing: @Composable () -> Unit = {}) {
    val error = vm.errorMessage
    if (error != null && vm.members.isEmpty() && !vm.isLoading) {
        LoadFailedState(error, onRetry = { vm.load() })
        return
    }

    val focusManager = LocalFocusManager.current
    // Held by the view model so the scroll position survives pushes and tab switches.
    val listState = vm.listState
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }
    // ~3700 members: filter and count only when the list, the search or the chip changes.
    val filtered = remember(vm.members, vm.query, vm.filter) { vm.filteredMembers }
    val counts = remember(vm.members) { MembersFilter.entries.associateWith { vm.count(it) } }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Miembros", style = NovoFonts.heading(32))
                            Text(vm.summaryLabel, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
                        }
                        headerTrailing()
                    }
                }
                item(key = "search") {
                    SearchField(
                        vm.query,
                        vm::updateQuery,
                        "Buscar por nombre o cédula",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        showsClear = true,
                    )
                }
                item(key = "chips") { FilterChips(vm, counts) }
                when {
                    vm.isLoading && vm.members.isEmpty() -> items(8) { MemberRowSkeleton(Modifier.padding(horizontal = 16.dp)) }
                    filtered.isEmpty() -> item { MembersEmptyState(hasMembers = vm.members.isNotEmpty()) }
                    else -> items(filtered, key = { it.id }) { member ->
                        MemberRow(
                            member,
                            Modifier
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(NovoDimens.cornerRadius))
                                .clickable { vm.openMember(member) },
                        )
                    }
                }
            }
        }

        // Floating "+" — opens the Nuevo miembro form.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .shadow(12.dp, CircleShape, ambientColor = NovoColors.accent, spotColor = NovoColors.accent)
                .size(56.dp)
                .clip(CircleShape)
                .background(NovoColors.accent)
                .clickable { vm.openCreate() },
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Nuevo miembro", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

/** Same chips as the coach Evaluaciones / Programas lists. */
@Composable
private fun FilterChips(vm: MembersViewModel, counts: Map<MembersFilter, Int>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        MembersFilter.entries.forEach { filter ->
            val selected = vm.filter == filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) NovoColors.accent else NovoColors.card)
                    .clickable { vm.selectFilter(filter) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                val color = if (selected) Color.White else Color(0xFF9A9A9E)
                Text(filter.label, style = NovoFonts.semibold(14).copy(color = color))
                Text("${counts[filter] ?: 0}", style = NovoFonts.semibold(12).copy(color = color.copy(alpha = 0.85f)))
            }
        }
    }
}

@Composable
private fun MembersEmptyState(hasMembers: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    ) {
        Icon(Icons.Outlined.PersonSearch, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(34.dp))
        Text(if (hasMembers) "Sin resultados" else "Sin miembros", style = NovoFonts.semibold(15))
        if (hasMembers) {
            Text(
                "No encontramos miembros que coincidan con tu búsqueda.",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Equivalent of iOS ContentUnavailableView("No se pudo cargar", …) — plus a retry button. */
@Composable
internal fun LoadFailedState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(44.dp))
        Text("No se pudo cargar", style = NovoFonts.semibold(20))
        Text(
            message,
            style = NovoFonts.body(14).copy(color = NovoColors.textSecondary),
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            SecondaryButton("Reintentar", onRetry, Modifier.padding(top = 8.dp))
        }
    }
}
