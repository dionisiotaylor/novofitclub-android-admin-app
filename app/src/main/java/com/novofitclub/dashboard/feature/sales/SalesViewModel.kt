package com.novofitclub.dashboard.feature.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.imageUrl
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.members.Member
import com.novofitclub.dashboard.feature.members.MemberDetailState
import com.novofitclub.dashboard.feature.members.MemberEditState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.Date

/**
 * One row's worth of display data, built from a sale's data
 * (`novo-staff/v1/sales/list` sends it with each sale) — see [summaryFrom].
 */
data class SaleSummaryRow(
    val id: String,
    val receipt: String?,
    val date: String?,
    val buyer: String?,
    val product: String?,
    val amount: Double?,
    val imageUrl: String?,
    /** Has a "Diferencia / Ajuste" line — the row shows purple. */
    val hasAjuste: Boolean = false,
    /** Paid by canje (status "trade") — the row shows blue and ₡0. */
    val isCanje: Boolean = false,
)

data class ReceptionistUser(val id: String, val name: String)

data class SalesTotals(val total: Double, val count: Int)

/** In-tab navigation of the Ventas tab (the iOS NavigationStack). */
sealed interface SalesRoute {
    data object List : SalesRoute
    data object Dashboard : SalesRoute
    data class Detail(val saleId: String) : SalesRoute
    /** A member opened from a sale's Miembros section. */
    class MemberDetail(val state: MemberDetailState) : SalesRoute
    class MemberEdit(val state: MemberEditState, val detail: MemberDetailState) : SalesRoute
}

/**
 * Port of iOS `SalesViewModel`. Activity-scoped (see [SalesTab]) so the list,
 * filters and in-tab back stack survive switching tabs, like iOS's TabView.
 */
class SalesViewModel(
    private val api: WPApiClient,
    /** `can.edit_member_dates` of the signed-in user, read when a member edit opens. */
    private val canEditDates: () -> Boolean = { false },
) : ViewModel() {
    var rows by mutableStateOf<List<SaleSummaryRow>>(emptyList())
        private set
    var searchText by mutableStateOf("")
        private set
    var isLoadingFirstPage by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var isFetchingPage by mutableStateOf(false)
        private set

    var selectedDate by mutableStateOf<Date?>(null)
        private set
    var selectedReceptionist by mutableStateOf<ReceptionistUser?>(null)
        private set
    var receptionists by mutableStateOf<List<ReceptionistUser>>(emptyList())
        private set

    var todayTotals by mutableStateOf<SalesTotals?>(null)
        private set
    var monthTotals by mutableStateOf<SalesTotals?>(null)
        private set
    var isLoadingSummary by mutableStateOf(false)
        private set

    /** Back stack of the tab; the list is always at the bottom. */
    var backStack by mutableStateOf<List<SalesRoute>>(listOf(SalesRoute.List))
        private set

    private var page = 1
    private val pageSize = 25
    private var pageJob: Job? = null
    private var searchJob: Job? = null
    private var didInitialLoad = false

    /** The search the server is asked for: 2+ characters, else none. */
    private val activeSearch: String get() = searchText.trim().takeIf { it.length >= 2 }.orEmpty()
    val isSearching: Boolean get() = activeSearch.isNotEmpty()

    /** The server already filtered them (search, day, receptionist). */
    val filteredRows: List<SaleSummaryRow> get() = rows

    /** Searches name, cédula or receipt on the server, 300 ms after the last keystroke. */
    fun updateSearch(text: String) {
        val before = activeSearch
        searchText = text
        if (activeSearch == before) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            resetAndReload()
        }
    }

    // region Navigation

    fun push(route: SalesRoute) {
        backStack = backStack + route
    }

    fun pop() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }

    /** Pops [route] only if it's still on top (a save may finish after the user already left). */
    fun popIfTop(route: SalesRoute) {
        if (backStack.last() === route) pop()
    }

    fun openMember(member: Member) {
        push(SalesRoute.MemberDetail(MemberDetailState(member, api, viewModelScope)))
    }

    fun openMemberEdit(detail: MemberDetailState) {
        push(SalesRoute.MemberEdit(MemberEditState(detail.member.id, api, viewModelScope, canEditDates()), detail))
    }

    // endregion

    /** The iOS `.task` — first page, receptionists and summary in parallel, once. */
    fun initialLoad() {
        if (didInitialLoad) return
        didInitialLoad = true
        viewModelScope.launch { loadFirstPage() }
        viewModelScope.launch { loadReceptionists() }
        viewModelScope.launch { loadSummary() }
    }

    private suspend fun loadFirstPage() {
        if (rows.isNotEmpty()) return
        isLoadingFirstPage = true
        errorMessage = null
        loadPage()
        // A reset may have started a newer load while this one was waiting.
        if (!isFetchingPage) isLoadingFirstPage = false
    }

    fun retry() {
        viewModelScope.launch { resetAndReload() }
        viewModelScope.launch { loadSummary() }
    }

    /** Pull-to-refresh. */
    fun refresh(then: () -> Unit = {}) {
        viewModelScope.launch {
            isRefreshing = true
            coroutineScope {
                launch { resetAndReload() }
                launch { loadSummary() }
            }
            isRefreshing = false
            then()
        }
    }

    /**
     * Server-computed totals from `novo-push/v1/sales-summary` — the app
     * never sums this itself. When a date filter is active, passes it along
     * so "today" becomes that date's totals and "month" becomes that date's
     * month-to-date instead of always today.
     */
    private suspend fun loadSummary() {
        if (isLoadingSummary) return
        isLoadingSummary = true
        try {
            val query = mutableMapOf<String, String>()
            selectedDate?.let { query["date"] = Formatters.apiDate(it) }
            val data = try {
                api.getObject("novo-push/v1/sales-summary", query)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
            todayTotals = parseTotals(data.obj("today"))
            monthTotals = parseTotals(data.obj("month"))
        } finally {
            isLoadingSummary = false
        }
    }

    private fun parseTotals(record: JSONObject?): SalesTotals? {
        record ?: return null
        return SalesTotals(total = record.num("total") ?: 0.0, count = (record.num("count") ?: 0.0).toInt())
    }

    fun setDate(date: Date?) {
        val normalized = date?.let { startOfDay(it) }
        if (selectedDate == normalized) return
        selectedDate = normalized
        viewModelScope.launch { resetAndReload() }
        viewModelScope.launch { loadSummary() }
    }

    fun setReceptionist(user: ReceptionistUser?) {
        if (selectedReceptionist?.id == user?.id) return
        selectedReceptionist = user
        viewModelScope.launch { resetAndReload() }
    }

    /**
     * Users whose role slug/name contains "recep" (i.e. "recepcionista" /
     * "receptionist"), plus anyone in [extraReceptionistNames] — loaded
     * once, best-effort.
     */
    private suspend fun loadReceptionists() {
        if (receptionists.isNotEmpty()) return
        val raw = try {
            api.getArray("wp/v2/users", mapOf("per_page" to "100", "context" to "edit"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        receptionists = raw.mapNotNull { fields ->
            val id = fields.firstString("id") ?: return@mapNotNull null
            val name = fields.firstString("name", "display_name") ?: "Usuario #$id"
            val rolesArray = fields.arr("roles")
            val roles = (0 until (rolesArray?.length() ?: 0)).mapNotNull { rolesArray?.optString(it)?.lowercase() }
            val hasReceptionistRole = roles.any { it.contains("recep") }
            val isNamedException = extraReceptionistNames.any { it.equals(name, ignoreCase = true) }
            if (hasReceptionistRole || isNamedException) ReceptionistUser(id, name) else null
        }
    }

    /**
     * Called by the list once the last loaded row is on screen (and again
     * whenever a fetch finishes while it still is) — fetches the next page.
     */
    fun loadNextPage() {
        if (rows.isEmpty() || !hasMore || isLoadingMore || isFetchingPage) return
        viewModelScope.launch {
            isLoadingMore = true
            loadPage()
            isLoadingMore = false
        }
    }

    /** Drops the row of a sale deleted from its detail screen. */
    fun removeRow(saleId: String) {
        rows = rows.filterNot { it.id == saleId }
    }

    /** Re-reads one row after its sale was edited from the detail screen. */
    fun refreshRow(saleId: String) {
        val existing = rows.firstOrNull { it.id == saleId } ?: return
        viewModelScope.launch {
            val details = try {
                api.getObject("sale-creator/v1/sales/$saleId")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch
            }
            val updated = summaryFrom(details, saleId, existing.receipt, existing.date)
            rows = rows.map { if (it.id == saleId) updated else it }
        }
    }

    /**
     * Cancels any page fetch still in flight (e.g. a pull-to-refresh landing
     * while the initial load is running) so it can't append stale rows after
     * the reset.
     */
    private suspend fun resetAndReload() {
        pageJob?.cancel()
        pageJob = null
        isFetchingPage = false
        rows = emptyList()
        page = 1
        hasMore = true
        isLoadingMore = false
        loadFirstPage()
    }

    private suspend fun loadPage() {
        if (pageJob?.isActive == true) {
            pageJob?.join()
            return
        }
        val job = viewModelScope.launch {
            isFetchingPage = true
            try {
                // One call per page: each sale comes with its data, so the
                // rows don't need a second request each.
                val response = api.getObject("novo-staff/v1/sales/list", currentQuery())
                val seenIds = rows.map { it.id }.toMutableSet()
                val fresh = response.arr("items")?.objects().orEmpty().mapNotNull { sale ->
                    val id = sale.firstString("id") ?: return@mapNotNull null
                    if (!seenIds.add(id)) return@mapNotNull null
                    summaryFrom(sale, id, sale.str("receipt"), Formatters.friendlyDate(sale.str("date")))
                }
                rows = rows + fresh
                hasMore = response.optBoolean("has_more") && fresh.isNotEmpty()
                page += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                if (pageJob == null || pageJob == coroutineContext[Job]) isFetchingPage = false
            }
        }
        pageJob = job
        job.join()
    }

    /** author=0 is everyone's sales (the server keeps reception to her own regardless). */
    private fun currentQuery(): Map<String, String> {
        val query = mutableMapOf(
            "per_page" to pageSize.toString(),
            "page" to page.toString(),
            "author" to (selectedReceptionist?.id ?: "0"),
        )
        selectedDate?.let { query["date"] = Formatters.apiDate(it) }
        activeSearch.takeIf { it.isNotEmpty() }?.let { query["search"] = it }
        return query
    }

    companion object {
        /**
         * People who work the front desk but whose WordPress role doesn't
         * literally say "receptionist" — add their exact display name here to
         * include them in the filter regardless of role.
         */
        private val extraReceptionistNames = listOf("Fabiola Villalobos")

        fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        /** A list row from a sale's data (`novo-staff/v1/sales/list` item or `sale-creator/v1/sales/{id}`). */
        fun summaryFrom(details: JSONObject, id: String, receipt: String?, date: String?): SaleSummaryRow {
            val names = (details.arr("members")?.objects() ?: emptyList()).mapNotNull { member ->
                member.str("name")?.let { splitEmbeddedId(it).first }
            }
            // A sale doesn't always have a membership — a pure
            // physical-product sale has `membership: null`.
            val membership = details.obj("membership")
            val physicalProducts = details.arr("physical_products")?.objects() ?: emptyList()
            val services = details.arr("services")?.objects() ?: emptyList()
            val ajustes = details.arr("ajustes")?.objects().orEmpty().map { SaleAjuste.from(it) }
            val isCanje = details.firstString("status") == "trade"

            val product = membership?.firstString("title", "name")
                ?: physicalProducts.firstOrNull()?.firstString("title", "name")
                ?: services.firstOrNull()?.firstString("title", "name")
                ?: ajustes.firstOrNull()?.let { "Diferencia: ${it.concept}" }

            val membershipPrice = membership?.firstNumber("price") ?: 0.0
            val productsTotal = physicalProducts.sumOf { (it.firstNumber("price") ?: 0.0) * (it.firstNumber("quantity") ?: 1.0) }
            val servicesTotal = services.sumOf { it.firstNumber("price") ?: 0.0 }
            // Nothing is charged for a canje — ₡0, as in the wp-admin Sales list.
            val amount = if (isCanje) 0.0 else membershipPrice + productsTotal + servicesTotal + ajustes.sumOf { it.amount }

            return SaleSummaryRow(
                id = id,
                receipt = receipt,
                date = date,
                buyer = names.takeIf { it.isNotEmpty() }?.joinToString(", "),
                product = product,
                amount = amount,
                imageUrl = physicalProducts.firstOrNull()?.imageUrl() ?: membership?.imageUrl(),
                hasAjuste = ajustes.isNotEmpty(),
                isCanje = isCanje,
            )
        }
    }
}
