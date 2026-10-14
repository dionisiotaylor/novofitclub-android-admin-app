package com.novofitclub.dashboard.feature.members

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoDimens
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.feature.sales.SaleDetailScreen

/** Root of the Miembros tab: the member list plus an in-tab back stack. */
@Composable
fun MembersTab(app: NovoFitClubApp) {
    val vm: MembersViewModel = viewModel { MembersViewModel(app.api) }

    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        when (val route = vm.stack.lastOrNull()) {
            null -> MembersListScreen(vm)
            is MembersRoute.Detail -> MemberDetailScreen(
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
                onChanged = { route.detail.loadSales() },
            )
            is MembersRoute.Create -> MemberCreateScreen(
                state = route.state,
                vm = vm,
                onCancel = vm::pop,
                onCreated = {
                    vm.popIfTop(route)
                    vm.load()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersListScreen(vm: MembersViewModel) {
    val error = vm.errorMessage
    if (error != null && vm.members.isEmpty() && !vm.isLoading) {
        LoadFailedState(error, onRetry = vm::load)
        return
    }

    val focusManager = LocalFocusManager.current
    // Held by the view model so the scroll position survives pushes and tab switches.
    val listState = vm.listState
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Title, search and chips stay put; only the rows scroll.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NovoColors.background)
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Miembros", style = NovoFonts.heading(32))
                    Text(vm.summaryLabel, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
                }
                SearchField(vm.query, vm::updateQuery, onSubmit = { focusManager.clearFocus() })
                FilterChips(vm)
            }

            PullToRefreshBox(
                isRefreshing = vm.isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val filtered = vm.filteredMembers
                val displayed = filtered.take(vm.revealedCount)
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        vm.isLoading && vm.members.isEmpty() -> items(8) { MemberRowSkeleton() }
                        filtered.isEmpty() -> item { MembersEmptyState(hasMembers = vm.members.isNotEmpty()) }
                        else -> {
                            items(displayed, key = { it.id }) { member ->
                                LaunchedEffect(member.id, displayed.size) {
                                    vm.revealMoreIfNeeded(member, displayed, filtered.size)
                                }
                                MemberRow(
                                    member,
                                    Modifier
                                        .clip(RoundedCornerShape(NovoDimens.cornerRadius))
                                        .clickable { vm.openMember(member) },
                                )
                            }
                            if (vm.isLoadingMore && vm.revealedCount >= filtered.size) {
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            color = NovoColors.accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            "Cargando más…",
                                            style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                                        )
                                    }
                                }
                            }
                        }
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

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NovoColors.card)
            .padding(horizontal = 14.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = NovoColors.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Buscar por nombre", style = NovoFonts.body(16).copy(color = NovoColors.textSecondary))
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = NovoFonts.body(16),
                cursorBrush = SolidColor(NovoColors.accent),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Borrar búsqueda",
                tint = NovoColors.textSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun FilterChips(vm: MembersViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MembersFilter.entries) { filter ->
            val active = vm.filter == filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (active) NovoColors.accent else NovoColors.card)
                    .border(1.dp, if (active) NovoColors.accent else NovoColors.separator, CircleShape)
                    .clickable { vm.selectFilter(filter) }
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    filter.label,
                    style = NovoFonts.semibold(13).copy(color = if (active) Color.White else Color(0xFFB0B0B0)),
                )
                Text(
                    vm.count(filter).toString(),
                    style = NovoFonts.semibold(11).copy(color = if (active) Color.White else Color(0xFFB3B3B3)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (active) 0.25f else 0.08f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
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
