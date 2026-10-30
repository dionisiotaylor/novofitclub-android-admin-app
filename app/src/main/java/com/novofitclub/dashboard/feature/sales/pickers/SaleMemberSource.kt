package com.novofitclub.dashboard.feature.sales.pickers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.feature.members.MemberIndex
import com.novofitclub.dashboard.feature.members.foldForSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The subset of the Members tab's `Member` the sale member picker needs,
 * parsed from `novo-staff/v1/members/index` (same keys as sale-creator/v1/members): `id` (member post id), `member_id`
 * (cédula), `name` ("Real Name – member_id") and `photo_url`.
 */
data class PickerMember(
    val id: String,
    val memberId: String,
    val name: String,
    val initials: String,
    val avatarColor: Color,
    val photoUrl: String?,
    val age: Int? = null,
) {
    val searchName: String = foldForSearch(name)
    val cedulaDigits: String = memberId.filter(Char::isLetterOrDigit).uppercase()

    companion object {
        fun from(fields: JSONObject, fallbackIndex: Int): PickerMember {
            val id = fields.str("id") ?: "row-$fallbackIndex"
            val memberId = fields.firstString("member_id") ?: id
            val raw = fields.firstString("name", "nombre", "display_name", "full_name") ?: ""
            val name = splitEmbeddedId(raw).first ?: "Miembro #$memberId"
            return PickerMember(
                id = id,
                memberId = memberId,
                name = name,
                initials = initials(name),
                avatarColor = NovoColors.avatarPalette[fallbackIndex % NovoColors.avatarPalette.size],
                photoUrl = fields.firstString("photo_url"),
                age = fields.firstNumber("age")?.toInt(),
            )
        }
    }
}

/**
 * The member picker's catalog: every member from the shared [MemberIndex]
 * (one call, kept on the phone), searched on the phone by name — without
 * case or accents — or cédula.
 */
class SaleMemberSource(private val api: WPApiClient) : ViewModel() {
    var members by mutableStateOf<List<PickerMember>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set

    /** The matches for [query], recomputed only when it or the list changes. */
    var filteredMembers by mutableStateOf<List<PickerMember>>(emptyList())
        private set

    private var loadJob: Job? = null

    fun updateQuery(value: String) {
        query = value
        filteredMembers = filter(members, value)
    }

    /** The cached list right away, then a fresh one (reused if Miembros just loaded it). */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            errorMessage = null
            if (members.isEmpty()) MemberIndex.cached()?.let { show(it) }
            isLoading = members.isEmpty()
            try {
                show(MemberIndex.fetch(api))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (members.isEmpty()) errorMessage = e.message
            }
            isLoading = false
        }
    }

    private suspend fun show(items: List<JSONObject>) {
        members = withContext(Dispatchers.Default) { items.mapIndexed { index, fields -> PickerMember.from(fields, index) } }
        filteredMembers = filter(members, query)
    }

    private fun filter(list: List<PickerMember>, query: String): List<PickerMember> {
        if (query.isBlank()) return list
        val name = foldForSearch(query.trim())
        val digits = query.filter(Char::isLetterOrDigit).uppercase()
        return list.filter { member ->
            member.searchName.contains(name) || (digits.length >= 3 && member.cedulaDigits.contains(digits))
        }
    }
}
