package com.novofitclub.dashboard.feature.members

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.coach.hexColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * The member screen's server-side half, from `novo-staff/v1/members/{id}`:
 * the same data the wp-admin member edit screen shows
 * (themes/.../app/members/edit-screen.php) — status and plan type, the
 * membership dates, the Programs list and the change history.
 */
data class MemberOverview(
    val statusLabel: String?,
    val typeLabel: String?,
    val typeColor: Color?,
    val age: Int?,
    val expiration: LocalDate?,
    val originalExpiration: LocalDate?,
    val lastPayment: LocalDate?,
    val paymentDay: LocalDate?,
    val programs: List<Program>,
    val history: List<HistoryEntry>,
    val historySince: LocalDate?,
) {
    /** [isCurrent]: the newest program assigned (by the program's own date) — "Actual" on the web; the rest are "Anterior". */
    data class Program(val id: Int, val title: String, val date: LocalDate?, val isCurrent: Boolean)

    /** [isHidden]: a contact field the signed-in role may not read (coaches) — only "cambió" is shown, same as the web. */
    data class HistoryChange(val label: String, val from: String?, val to: String?, val isHidden: Boolean)

    data class HistoryEntry(
        val id: Int,
        val actor: String,
        val source: String?,
        val isCreation: Boolean,
        val date: Date?,
        val changes: List<HistoryChange>,
    )

    /** Newest first, the way the web lists them. */
    val programsNewestFirst: List<Program> get() = programs.sortedByDescending { it.date ?: LocalDate.MIN }

    companion object {
        val displayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun day(raw: String?): LocalDate? = MemberDateFormats.parse(raw, MemberDateFormats.iso)

        fun from(fields: JSONObject): MemberOverview {
            val type = fields.obj("membership_type")
            val dates = fields.obj("dates")
            return MemberOverview(
                statusLabel = fields.firstString("status_label"),
                typeLabel = type?.firstString("label"),
                typeColor = hexColor(type?.firstString("color")),
                age = fields.firstNumber("age")?.toInt(),
                expiration = day(dates?.firstString("expiration")),
                originalExpiration = day(dates?.firstString("original_expiration")),
                lastPayment = day(dates?.firstString("last_payment")),
                paymentDay = day(dates?.firstString("payment_day")),
                programs = fields.arr("programs")?.objects().orEmpty().mapNotNull { p ->
                    val id = p.firstNumber("id")?.toInt() ?: return@mapNotNull null
                    Program(id, p.firstString("title") ?: "Programa", day(p.firstString("date")), p.bool("current") ?: false)
                },
                history = fields.arr("history")?.objects().orEmpty().mapNotNull { h ->
                    val id = h.firstNumber("id")?.toInt() ?: return@mapNotNull null
                    HistoryEntry(
                        id = id,
                        actor = h.firstString("actor") ?: "—",
                        source = h.firstString("source"),
                        isCreation = h.firstString("action") == "created",
                        date = Formatters.parseDate(h.firstString("date")),
                        changes = h.arr("changes")?.objects().orEmpty().map { c ->
                            HistoryChange(c.firstString("label").orEmpty(), c.firstString("from"), c.firstString("to"), c.bool("hidden") ?: false)
                        },
                    )
                },
                historySince = day(fields.firstString("history_since")),
            )
        }

        /** Whole days from today to [date] (negative once it's past). */
        fun daysFromToday(date: LocalDate): Int = ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()

        /** "Hoy, 10:53" / "Ayer, 18:02" / "12/10/2026, 09:15" — the web's wording. */
        fun whenLabel(date: Date): String {
            val day = date.toLocalDate()
            val time = Formatters.timeOnly(date)
            return when (day) {
                LocalDate.now() -> "Hoy, $time"
                LocalDate.now().minusDays(1) -> "Ayer, $time"
                else -> "${day.format(displayFormat)}, $time"
            }
        }
    }
}

/** Loads [MemberOverview] and assigns / removes programs. */
class MemberOverviewState(
    private val memberId: String,
    private val api: WPApiClient,
    private val scope: CoroutineScope,
    private val onLoaded: (MemberOverview) -> Unit,
) {
    var overview by mutableStateOf<MemberOverview?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    /** A failed assign/remove — shown in the Programas card. */
    var programError by mutableStateOf<String?>(null)
        private set
    var busyProgramId by mutableStateOf<Int?>(null)
        private set

    fun load() {
        scope.launch { fetch() }
    }

    private suspend fun fetch() {
        isLoading = overview == null
        errorMessage = null
        try {
            set(MemberOverview.from(api.getObject("novo-staff/v1/members/$memberId")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "No se pudo cargar."
        }
        isLoading = false
    }

    fun assignProgram(programId: Int) = changePrograms(programId) {
        api.postObject("novo-staff/v1/members/$memberId/programs", JSONObject().put("program_id", programId))
    }

    fun removeProgram(programId: Int) = changePrograms(programId) {
        api.deleteReturningObject("novo-staff/v1/members/$memberId/programs/$programId")
    }

    /**
     * Both calls answer with the whole member screen; it's fetched once more
     * right after, because the server writes the change history at the end
     * of the request (history.php) — after that answer was built.
     */
    private fun changePrograms(programId: Int, request: suspend () -> JSONObject) {
        scope.launch {
            busyProgramId = programId
            programError = null
            try {
                set(MemberOverview.from(request()))
                busyProgramId = null
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                programError = e.localizedMessage
            }
            busyProgramId = null
        }
    }

    private fun set(value: MemberOverview) {
        overview = value
        onLoaded(value)
    }
}
