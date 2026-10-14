package com.novofitclub.dashboard.feature.sales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.feature.sales.dashboard.SalesDashboardScreen
import com.novofitclub.dashboard.feature.sales.edit.SaleEditScreen

/**
 * Root of the Ventas tab: the sales list, with the sale detail and the sales
 * dashboard pushed on top of it, and "Nueva venta" presented as a sheet-like
 * overlay. The list's view model (and the tab's back stack) is
 * activity-scoped so it survives switching tabs.
 */
@Composable
fun SalesTab(app: NovoFitClubApp) {
    val vm: SalesViewModel = viewModel(key = "sales-tab") { SalesViewModel(app.api) }
    val listState = rememberLazyListState()
    var showingNewSale by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.initialLoad() }

    BackHandler(enabled = vm.backStack.size > 1, onBack = vm::pop)

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        when (val route = vm.backStack.last()) {
            SalesRoute.List -> SalesListScreen(
                vm = vm,
                listState = listState,
                onOpenSale = { vm.push(SalesRoute.Detail(it)) },
                onOpenDashboard = { vm.push(SalesRoute.Dashboard) },
                onNewSale = { showingNewSale = true },
            )
            SalesRoute.Dashboard -> ScopedViewModels {
                SalesDashboardScreen(app, onBack = vm::pop)
            }
            is SalesRoute.Detail -> key(route.saleId) {
                ScopedViewModels {
                    SaleDetailContent(
                        app = app,
                        saleId = route.saleId,
                        onBack = vm::pop,
                        onEdited = { vm.refreshRow(route.saleId) },
                        onDeleted = {
                            vm.removeRow(route.saleId)
                            vm.pop()
                        },
                    )
                }
            }
        }

        if (showingNewSale) {
            OverlayLayer {
                ScopedViewModels {
                    SaleEditScreen(
                        app = app,
                        saleId = null,
                        onDismiss = { showingNewSale = false },
                        onSaved = { vm.refresh() },
                    )
                }
            }
        }
    }
}
