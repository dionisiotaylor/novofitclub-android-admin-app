package com.novofitclub.dashboard.feature.sales.pickers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The subset of the Members tab's `Member` the sale member picker needs,
 * parsed from `sale-creator/v1/members`: `id` (member post id), `member_id`
 * (cédula) and `name` ("Real Name – member_id").
 */
data class PickerMember(
    val id: String,
    val memberId: String,
    val name: String,
    val initials: String,
    val avatarColor: Color,
) {
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
            )
        }
    }
}

/**
 * Port of the parts of iOS `MembersViewModel` the member picker uses: the
 * eager, progressively-paged member catalog plus client-side search, shown
 * 20 at a time as the user scrolls.
 */
class SaleMemberSource(private val api: WPApiClient) : ViewModel() {
    var members by mutableStateOf<List<PickerMember>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set

    private var revealedCount by mutableIntStateOf(REVEAL_BATCH)
    private var started = false

    fun updateQuery(value: String) {
        query = value
        revealedCount = REVEAL_BATCH
    }

    /**
     * First page renders immediately; remaining pages keep loading in the
     * background and append as they arrive.
     */
    fun load() {
        if (started) return
        started = true
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            members = emptyList()
            var page = 1
            while (page <= MAX_PAGES) {
                try {
                    val response = api.getObject(
                        "sale-creator/v1/members",
                        mapOf("per_page" to PER_PAGE.toString(), "page" to page.toString()),
                    )
                    val items = response.arr("items")?.objects() ?: emptyList()
                    if (items.isEmpty()) break
                    val start = members.size
                    members = members + items.mapIndexed { offset, fields -> PickerMember.from(fields, start + offset) }
                    if (page == 1) {
                        isLoading = false
                        isLoadingMore = true
                    }
                    if (response.bool("has_more") != true) break
                    page += 1
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (page == 1) errorMessage = e.message
                    break
                }
            }
            isLoading = false
            isLoadingMore = false
        }
    }

    val filteredMembers: List<PickerMember>
        get() = if (query.isEmpty()) members else members.filter { it.name.contains(query, ignoreCase = true) }

    val displayedMembers: List<PickerMember>
        get() = filteredMembers.take(revealedCount)

    /** Reveals the next batch once the last currently-shown row is composed. */
    fun revealMoreIfNeeded(current: PickerMember) {
        val visible = filteredMembers
        if (current.id != displayedMembers.lastOrNull()?.id || revealedCount >= visible.size) return
        revealedCount = minOf(revealedCount + REVEAL_BATCH, visible.size)
    }

    private companion object {
        const val REVEAL_BATCH = 20
        const val PER_PAGE = 100
        const val MAX_PAGES = 200
    }
}
