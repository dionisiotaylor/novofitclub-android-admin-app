package com.novofitclub.dashboard.feature.members

import com.novofitclub.dashboard.core.net.firstNumber
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.objects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** User-facing message for a failed request (WPApiException messages are already Spanish). */
internal fun Throwable.userMessage(): String = localizedMessage ?: message ?: "Error desconocido."

/** In-tab navigation destinations, kept in the view model so they survive tab switches. */
sealed interface MembersRoute {
    class Detail(val state: MemberDetailState) : MembersRoute
    class Edit(val state: MemberEditState, val detail: MemberDetailState) : MembersRoute
    class Sale(val saleId: String, val detail: MemberDetailState) : MembersRoute
    class Create(val state: MemberCreateState) : MembersRoute
    data object Freezes : MembersRoute
}

/**
 * Miembros list: every member from `novo-staff/v1/members/index` in one call
 * ([MemberIndex]), shown at once from the copy kept on the phone while a fresh
 * one loads. Search and filters run on the phone over the whole club.
 */
class MembersViewModel(
    private val api: WPApiClient,
    /** `can.edit_member_dates` of the signed-in user, read when an edit opens. */
    private val canEditDates: () -> Boolean = { false },
) : ViewModel() {
    var members by mutableStateOf<List<Member>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf(MembersFilter.All)
        private set

    val stack = mutableStateListOf<MembersRoute>()

    /** List scroll position, kept here so it survives in-tab pushes and tab switches. */
    val listState = LazyListState()

    private var loadJob: Job? = null

    init {
        load(force = false)
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun selectFilter(value: MembersFilter) {
        filter = value
    }

    fun refresh() {
        isRefreshing = true
        load()
    }

    /** The cached list first (instant), then the server's. A failure keeps what's shown. */
    fun load(force: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            errorMessage = null
            if (members.isEmpty()) MemberIndex.cached()?.let { members = parse(it) }
            isLoading = members.isEmpty()
            try {
                members = parse(MemberIndex.fetch(api, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (members.isEmpty()) errorMessage = e.userMessage()
            }
            isLoading = false
            isRefreshing = false
        }
    }

    private suspend fun parse(items: List<org.json.JSONObject>): List<Member> =
        withContext(Dispatchers.Default) { items.mapIndexed { index, fields -> Member.from(fields, index) } }

    fun count(filter: MembersFilter): Int {
        val status = filter.status ?: return members.size
        return members.count { it.status == status }
    }

    /** Name without case or accents, cédula ignoring dashes and spaces, or phone digits. */
    val filteredMembers: List<Member>
        get() {
            val name = foldForSearch(query.trim())
            val digits = query.filter(Char::isLetterOrDigit).uppercase()
            val number = query.filter(Char::isDigit)
            return members.filter { member ->
                (filter.status == null || member.status == filter.status) &&
                    (
                        name.isEmpty() || member.searchName.contains(name) ||
                            (digits.length >= 3 && member.cedulaDigits.contains(digits)) ||
                            (number.length >= 4 && member.phoneDigits.contains(number))
                        )
            }
        }

    val summaryLabel: String
        get() {
            val total = members.size
            val active = count(MembersFilter.Active)
            return "$total miembro${if (total == 1) "" else "s"} · $active activo${if (active == 1) "" else "s"}"
        }

    // Navigation -----------------------------------------------------------

    fun openMember(member: Member) {
        stack.add(MembersRoute.Detail(MemberDetailState(member, api, viewModelScope)))
    }

    fun openEdit(detail: MemberDetailState) {
        stack.add(MembersRoute.Edit(MemberEditState(detail.member.id, api, viewModelScope, canEditDates()), detail))
    }

    fun openSale(saleId: String, detail: MemberDetailState) {
        stack.add(MembersRoute.Sale(saleId, detail))
    }

    fun openFreezes() {
        stack.add(MembersRoute.Freezes)
    }

    fun openCreate() {
        stack.add(MembersRoute.Create(MemberCreateState(api, viewModelScope)))
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Pops [route] only if it's still on top (a save may finish after the user already left). */
    fun popIfTop(route: MembersRoute) {
        if (stack.lastOrNull() === route) pop()
    }

    /** Best-effort duplicate checks against the loaded list (the members index carries no emails). */
    fun memberWithCedula(cedula: String): Member? {
        val trimmed = cedula.trim()
        if (trimmed.isEmpty()) return null
        return members.firstOrNull { it.memberId == trimmed }
    }

    fun memberWithEmail(email: String): Member? {
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty()) return null
        return members.firstOrNull { it.email?.lowercase() == trimmed }
    }
}

/**
 * Member detail: the displayed (editable-after-save) values plus the member's
 * full sales history from `sale-creator/v1/members/{id}/sales`, loaded
 * eagerly across all pages and de-duplicated by id.
 */
class MemberDetailState(
    val member: Member,
    private val api: WPApiClient,
    private val scope: CoroutineScope,
) {
    var displayName by mutableStateOf(member.name)
        private set
    var displayPhone by mutableStateOf(member.phone)
        private set
    var displayEmail by mutableStateOf(member.email)
        private set
    var displayStatus by mutableStateOf(member.status)
        private set
    var displayStatusLabel by mutableStateOf(member.statusLabel)
        private set

    /** "Cambios guardados" toast, shown for 2 s after a successful save. */
    var showSavedToast by mutableStateOf(false)
        private set
    private var toastJob: Job? = null

    var sales by mutableStateOf<List<MemberSale>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var isFetching = false

    /** Membership dates, programs and history (`novo-staff/v1/members/{id}`). */
    val overview = MemberOverviewState(member.id, api, scope) { applyOverviewStatus(it) }

    init {
        loadSales()
        overview.load()
    }

    /** The status as stored (a sale sets it Activo right away), shown with the "por vencer" shade the list uses. */
    private fun applyOverviewStatus(value: MemberOverview) {
        val (status, label) = Member.status(value.statusLabel, value.expiration)
        displayStatus = status
        displayStatusLabel = label
    }

    /** Pull to refresh / after a sale changed: deleting one puts the member's dates back. */
    fun reload() {
        loadSales()
        overview.load()
    }

    val totalAmount: Double
        get() = sales.sumOf { it.amount }

    /** Guarded against overlapping runs (each would reset `sales` and duplicate rows). */
    fun loadSales() {
        if (isFetching) return
        isFetching = true
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                sales = emptyList()

                var page = 1
                val seen = mutableSetOf<String>()
                while (page <= SALES_MAX_PAGES) {
                    try {
                        val response = api.getObject(
                            "sale-creator/v1/members/${member.id}/sales",
                            mapOf("per_page" to SALES_PER_PAGE.toString(), "page" to page.toString()),
                        )
                        val items = response.optJSONArray("items")?.objects().orEmpty()
                        if (items.isEmpty()) break

                        var addedAny = false
                        val added = mutableListOf<MemberSale>()
                        for (fields in items) {
                            val id = fields.firstString("id") ?: UUID.randomUUID().toString()
                            if (!seen.add(id)) continue
                            added += MemberSale.from(id, fields)
                            addedAny = true
                        }
                        sales = sales + added

                        if (page == 1) {
                            isLoading = false
                            isLoadingMore = true
                        }
                        if (!addedAny || response.bool("has_more") != true) break
                        page += 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (page == 1) errorMessage = e.userMessage()
                        break
                    }
                }
            } finally {
                isLoading = false
                isLoadingMore = false
                isFetching = false
            }
        }
    }

    fun removeSale(id: String) {
        sales = sales.filterNot { it.id == id }
    }

    /** Mirrors the iOS edit sheet's onSaved callback. */
    fun applySaved(profile: MemberProfile) {
        displayName = profile.name.trim()
        displayPhone = profile.phone.trim().ifEmpty { null }
        displayEmail = profile.email.trim().ifEmpty { null }
        val (status, label) = Member.status(profile.status?.raw, profile.membershipExpirationDate)
        displayStatus = status
        displayStatusLabel = label
        overview.load()
        toastJob?.cancel()
        showSavedToast = true
        toastJob = scope.launch {
            delay(2000)
            showSavedToast = false
        }
    }

    private companion object {
        const val SALES_PER_PAGE = 50
        const val SALES_MAX_PAGES = 100
    }
}

/** Edit form backed by `GET`/`PATCH wp/v2/member/{id}`. */
class MemberEditState(
    val memberId: String,
    internal val api: WPApiClient,
    private val scope: CoroutineScope,
    /** Only the users allowed to change the membership dates edit (and send) them. */
    val canEditDates: Boolean = false,
) {
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    private var loadedProfile: MemberProfile? = null

    var name by mutableStateOf("")
    var cedula by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    /** Null while the member has no birthday and nobody picked one — never saved as today. */
    var birthday by mutableStateOf<LocalDate?>(null)
    var gender by mutableStateOf("male")
    var status by mutableStateOf(MemberAccountStatus.Active)
        private set
    var membershipExpirationDate by mutableStateOf<LocalDate?>(null)
    var paymentDay by mutableStateOf<LocalDate?>(null)
    var lastPaymentDate by mutableStateOf<LocalDate?>(null)
    var originalExpirationDate by mutableStateOf<LocalDate?>(null)
    var membershipType by mutableStateOf<String?>(null)
    var membershipTypeLabel by mutableStateOf<String?>(null)
        private set

    val canSave: Boolean
        get() = name.isNotBlank() && cedula.isNotBlank()

    init {
        scope.launch {
            try {
                val profile = MemberProfile.from(memberId, api.getObject("wp/v2/member/$memberId"))
                loadedProfile = profile
                name = profile.name
                cedula = profile.memberId
                phone = profile.phone
                email = profile.email
                birthday = profile.birthday
                gender = profile.gender ?: "male"
                status = profile.status ?: MemberAccountStatus.Active
                membershipExpirationDate = profile.membershipExpirationDate
                paymentDay = profile.paymentDay
                lastPaymentDate = profile.lastPaymentDate
                originalExpirationDate = profile.originalExpirationDate
                membershipType = profile.membershipType
                membershipTypeLabel = profile.membershipTypeLabel
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.userMessage()
            }
            isLoading = false
        }
    }

    fun save(onSaved: (MemberProfile) -> Unit) {
        val base = loadedProfile ?: return
        if (isSaving) return
        isSaving = true
        saveError = null
        val profile = base.copy(
            name = name,
            memberId = cedula,
            phone = phone,
            email = email,
            birthday = birthday,
            gender = gender,
            membershipExpirationDate = membershipExpirationDate,
            paymentDay = paymentDay,
            lastPaymentDate = lastPaymentDate,
            originalExpirationDate = originalExpirationDate,
            membershipType = membershipType,
        )
        scope.launch {
            try {
                api.patchObject("wp/v2/member/$memberId", profile.patchBody(includeLockedDates = canEditDates))
                onSaved(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = e.userMessage()
            }
            isSaving = false
        }
    }
}

/** Create form backed by `POST wp/v2/member`. */
class MemberCreateState(
    internal val api: WPApiClient,
    private val scope: CoroutineScope,
) {
    var draft by mutableStateOf(NewMemberDraft())

    /** Set once the member exists — the screen then moves on to the access photo step (it needs the new post id). */
    var createdMemberId by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    /** Tapping a domain completes the username — replacing anything from an existing "@". */
    fun applyEmailDomain(domain: String) {
        val username = draft.email.split("@", limit = 2).first()
        draft = draft.copy(email = username + domain)
    }

    /** [onCreated] runs right after the member exists (to reload the list); [onNoId] when the answer had no id to continue with. */
    fun save(onCreated: () -> Unit, onNoId: () -> Unit) {
        if (isSaving) return
        isSaving = true
        saveError = null
        scope.launch {
            try {
                val created = api.postObject("wp/v2/member", draft.createBody())
                onCreated()
                val id = created.firstNumber("id")?.toInt()
                if (id != null) createdMemberId = id.toString() else onNoId()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = e.userMessage()
            }
            isSaving = false
        }
    }

    companion object {
        val emailDomains = listOf("@gmail.com", "@hotmail.com", "@yahoo.com")
    }
}

/** A cédula as the server's duplicate check compares it: no spaces, dashes or dots, uppercase. */
internal fun cedulaKey(raw: String): String = raw.filterNot { it == ' ' || it == '-' || it == '.' }.uppercase()
