package com.novofitclub.dashboard.feature.freezes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.members.MemberDateFormats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

/** Congelamiento request types — `CongRestAPI::TYPE_LABELS`, with the web form's card texts (freeze-form.js TYPE_META). */
enum class FreezeType(val raw: String, val title: String, val detail: String, val icon: ImageVector) {
    Viaje("viaje", "Viaje", "Fuera del país", Icons.Outlined.Flight),
    Enfermedad("enfermedad", "Enfermedad", "Incapacidad necesaria", Icons.Outlined.MedicalServices),
    Otro("otro", "Otro", "Caso por caso — requiere aprobación del administrador", Icons.Outlined.Info),
    ;

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

enum class FreezeStatus(val raw: String, val label: String, val tabTitle: String) {
    Pendiente("pendiente", "Pendiente", "Pendientes"),
    Aprobado("aprobado", "Aprobado", "Aprobados"),
    Denegado("denegado", "Denegado", "Denegados"),
    Anulado("anulado", "Anulado", "Anulados"),
    ;

    val color: Color
        get() = when (this) {
            Pendiente -> NovoColors.statusExpiring
            Aprobado -> NovoColors.statusActive
            Denegado -> NovoColors.statusExpired
            Anulado -> NovoColors.textSecondary
        }

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

/** One request from `congelados/v1/freezes` (CongRestAPI::format_freeze()). */
data class Freeze(
    val id: Int,
    val memberId: Int?,
    val memberName: String,
    /** The member's current dates — for the "Aprobar" preview. */
    val memberExpiration: LocalDate?,
    val memberPaymentDay: LocalDate?,
    val type: FreezeType?,
    val typeLabel: String,
    val days: Int,
    val evidence: List<Evidence>,
    val status: FreezeStatus,
    val notes: String,
    val isArchived: Boolean,
    /** Captured when it was approved: what it moved, from → to. */
    val previousExpiration: LocalDate?,
    val newExpiration: LocalDate?,
    val previousPaymentDay: LocalDate?,
    val newPaymentDay: LocalDate?,
    val requesterName: String,
    val date: Date?,
    /** The server's own `edit_post` check: a receptionist edits only hers. */
    val canEdit: Boolean,
) {
    data class Evidence(val id: Int, val url: String?, val thumbUrl: String?)

    val daysLabel: String get() = if (days == 1) "1 día" else "$days días"

    /**
     * The date an approval moves [date] to. Inclusive count, as the server
     * does it (CongFreezeRepository::shift_member_date()): today is day 1,
     * so 25/10 + 11 días → 04/11.
     */
    fun shifted(date: LocalDate): LocalDate = date.plusDays(maxOf(0, days - 1).toLong())

    companion object {
        val displayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun from(record: JSONObject): Freeze? {
            val id = record.firstNumber("id")?.toInt() ?: return null
            fun day(raw: String?) = MemberDateFormats.parse(raw, MemberDateFormats.iso)
            val member = record.obj("member")
            val rawName = member?.firstString("name") ?: "—"
            val type = FreezeType.from(record.firstString("freeze_type"))
            val approved = record.obj("approved_dates")
            return Freeze(
                id = id,
                memberId = member?.firstNumber("id")?.toInt(),
                memberName = splitEmbeddedId(rawName).first ?: rawName,
                memberExpiration = day(member?.firstString("membership_expiration_date")),
                memberPaymentDay = day(member?.firstString("payment_day")),
                type = type,
                typeLabel = record.firstString("freeze_type_label") ?: type?.title ?: "—",
                days = (record.firstNumber("days_requested") ?: 0.0).toInt(),
                evidence = record.arr("evidence")?.objects().orEmpty().mapNotNull { e ->
                    val evidenceId = e.firstNumber("id")?.toInt() ?: return@mapNotNull null
                    Evidence(evidenceId, e.firstString("url"), e.firstString("thumb") ?: e.firstString("url"))
                },
                status = FreezeStatus.from(record.firstString("status")) ?: FreezeStatus.Pendiente,
                notes = record.firstString("notes").orEmpty(),
                isArchived = record.bool("archived") ?: false,
                previousExpiration = day(approved?.firstString("prev_expiration")),
                newExpiration = day(approved?.firstString("new_expiration")),
                previousPaymentDay = day(approved?.firstString("prev_payment_day")),
                newPaymentDay = day(approved?.firstString("new_payment_day")),
                requesterName = record.obj("requester")?.firstString("name") ?: "—",
                date = Formatters.parseDate(record.firstString("date")),
                canEdit = record.bool("can_edit") ?: false,
            )
        }
    }
}

/** A list tab: one per status, plus Archivados — the wp-admin list's views (freeze-list.js). */
sealed interface FreezesTab {
    val title: String

    data class Status(val status: FreezeStatus) : FreezesTab {
        override val title: String get() = status.tabTitle
    }

    data object Archived : FreezesTab {
        override val title = "Archivados"
    }

    companion object {
        val all: List<FreezesTab> = FreezeStatus.entries.map(::Status) + Archived
    }
}

/** Aprobar / Denegar / Anular / Archivar — administrators only (server-checked). */
enum class FreezeAction(val path: String, val button: String) {
    Approve("approve", "Aprobar"),
    Deny("deny", "Denegar"),
    Void("void", "Anular"),
    Archive("archive", "Archivar"),
}

/** The Congelamientos list — `congelados/v1/freezes`, one tab per status plus Archivados. */
class FreezesViewModel(private val api: WPApiClient) : ViewModel() {
    var tab by mutableStateOf<FreezesTab>(FreezesTab.Status(FreezeStatus.Pendiente))
        private set
    var search by mutableStateOf("")
        private set
    var items by mutableStateOf<List<Freeze>>(emptyList())
        private set
    var hasMore by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var page = 1
    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectTab(value: FreezesTab) {
        if (value == tab) return
        tab = value
        load()
    }

    fun updateSearch(value: String) {
        search = value
        load(debounce = true)
    }

    fun load(debounce: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(300)
            page = 1
            isLoading = true
            errorMessage = null
            try {
                val (freezes, more) = fetch(1)
                items = freezes
                hasMore = more
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
                items = emptyList()
            }
            isLoading = false
        }
    }

    fun loadMore() {
        if (!hasMore || isLoadingMore || isLoading) return
        viewModelScope.launch {
            isLoadingMore = true
            runCatching { fetch(page + 1) }.onSuccess { (freezes, more) ->
                page += 1
                items = items + freezes.filter { new -> items.none { it.id == new.id } }
                hasMore = more
            }
            isLoadingMore = false
        }
    }

    private suspend fun fetch(page: Int): Pair<List<Freeze>, Boolean> {
        val query = mutableMapOf("page" to page.toString())
        when (val t = tab) {
            is FreezesTab.Status -> {
                query["status"] = t.status.raw
                query["archived"] = "0"
            }
            FreezesTab.Archived -> query["archived"] = "1"
        }
        search.trim().takeIf { it.isNotEmpty() }?.let { query["search"] = it }
        val response = api.getObject("congelados/v1/freezes", query)
        return response.arr("items")?.objects().orEmpty().mapNotNull { Freeze.from(it) } to (response.bool("has_more") ?: false)
    }

    companion object {
        suspend fun perform(api: WPApiClient, action: FreezeAction, freezeId: Int): Freeze? =
            Freeze.from(api.postObject("congelados/v1/freezes/$freezeId/${action.path}", JSONObject()))

        /** Pending requests for the Miembros pill: the first page's count, and whether there are more. */
        suspend fun pendingCount(api: WPApiClient): Pair<Int, Boolean> {
            val response = api.getObject("congelados/v1/freezes", mapOf("status" to "pendiente", "archived" to "0"))
            return (response.arr("items")?.length() ?: 0) to (response.bool("has_more") ?: false)
        }
    }
}
