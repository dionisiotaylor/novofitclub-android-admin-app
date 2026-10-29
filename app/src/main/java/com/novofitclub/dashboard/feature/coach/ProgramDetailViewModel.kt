package com.novofitclub.dashboard.feature.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * One program: its delivery status, the evaluation summary
 * (`novo-staff/v1/programs/{id}`) and its routine, edited here and saved
 * whole through the web builder's own endpoint (`gym/v1/program-routines/{id}`).
 */
class ProgramDetailViewModel(private val api: WPApiClient, val programId: Int) : ViewModel() {
    var detail by mutableStateOf<ProgramDetail?>(null)
        private set
    /** The routine being edited; [savedDays] is what the server has. */
    var days by mutableStateOf<List<RoutineDay>>(emptyList())
        private set
    private var savedDays by mutableStateOf<List<RoutineDay>>(emptyList())
    var selectedDayId by mutableStateOf<String?>(null)

    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isUpdatingStatus by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var actionError by mutableStateOf<String?>(null)
    var savedOnce by mutableStateOf(false)
        private set

    init {
        load()
    }

    val isDirty: Boolean get() = days != savedDays

    val selectedIndex: Int? get() = days.indexOfFirst { it.id == selectedDayId }.takeIf { it >= 0 } ?: if (days.isEmpty()) null else 0

    fun load() {
        viewModelScope.launch {
            isLoading = true
            try {
                coroutineScope {
                    val record = async { api.getObject("novo-staff/v1/programs/$programId") }
                    val routine = async { api.getArray("gym/v1/program-routines/$programId") }
                    detail = ProgramDetail.from(record.await(), programId)
                    days = routine.await().map { RoutineDay.from(it) }
                    savedDays = days
                    selectedDayId = days.firstOrNull()?.id
                }
                loadError = null
            } catch (e: Exception) {
                loadError = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving = true
            try {
                api.postObject("gym/v1/program-routines/$programId", JSONObject().put("workouts", JSONArray(days.map { it.payload() })))
                savedDays = days
                savedOnce = true
                onSaved()
            } catch (e: Exception) {
                actionError = e.localizedMessage
            }
            isSaving = false
        }
    }

    fun setStatus(status: ProgramStatus, onChanged: () -> Unit) {
        if (detail?.status == status) return
        viewModelScope.launch {
            isUpdatingStatus = true
            try {
                api.postObject("novo-staff/v1/programs/$programId/status", JSONObject().put("status", status.raw))
                detail = detail?.copy(status = status)
                onChanged()
            } catch (e: Exception) {
                actionError = e.localizedMessage
            }
            isUpdatingStatus = false
        }
    }

    // ---- Days ----

    fun addDay() {
        val day = RoutineDay(name = "Día ${days.size + 1}")
        days = days + day
        selectedDayId = day.id
    }

    fun updateDay(id: String, name: String, label: String) {
        days = days.map { if (it.id == id) it.copy(name = name, label = label) else it }
    }

    fun duplicateDay(id: String) {
        val index = days.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val copy = days[index].withNewIds().copy(name = "Día ${days.size + 1}")
        days = days.toMutableList().apply { add(index + 1, copy) }
        selectedDayId = copy.id
    }

    fun deleteDay(id: String) {
        val index = days.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        days = days.filterNot { it.id == id }
        selectedDayId = days.getOrNull(minOf(index, days.size - 1))?.id
    }

    fun canMoveDay(id: String, offset: Int): Boolean {
        val index = days.indexOfFirst { it.id == id }
        return index >= 0 && (index + offset) in days.indices
    }

    fun moveDay(id: String, offset: Int) {
        if (!canMoveDay(id, offset)) return
        val index = days.indexOfFirst { it.id == id }
        days = days.toMutableList().apply { add(index + offset, removeAt(index)) }
    }

    /** Another program's days, added after these or in their place. */
    fun importDays(imported: List<RoutineDay>, replacing: Boolean) {
        val copies = imported.map { it.withNewIds() }
        days = if (replacing) copies else days + copies
        selectedDayId = copies.firstOrNull()?.id ?: days.firstOrNull()?.id
    }

    // ---- Items of the selected day ----

    private fun mutateDay(change: (List<RoutineItem>) -> List<RoutineItem>) {
        val index = selectedIndex ?: return
        days = days.toMutableList().apply { this[index] = this[index].copy(items = change(this[index].items)) }
    }

    fun addExercise(exercise: LibraryExercise) = mutateDay { it + RoutineItem.Single(RoutineExercise.from(exercise, rest = "1 min")) }

    /** A new circuit; returned so the screen can open it for its name and rounds. */
    fun addGroup(): RoutineItem.Group? {
        if (selectedIndex == null) return null
        val group = RoutineItem.Group(name = "Circuito", rounds = "3 rondas", exercises = emptyList())
        mutateDay { it + group }
        return group
    }

    fun addExercise(exercise: LibraryExercise, groupId: String) = mutateDay { items ->
        items.map { if (it is RoutineItem.Group && it.id == groupId) it.copy(exercises = it.exercises + RoutineExercise.from(exercise, rest = "")) else it }
    }

    fun updateGroup(groupId: String, name: String, rounds: String) = mutateDay { items ->
        items.map { if (it is RoutineItem.Group && it.id == groupId) it.copy(name = name, rounds = rounds) else it }
    }

    /** An exercise, on its own or inside a circuit. */
    fun updateExercise(exercise: RoutineExercise) = mutateDay { items ->
        items.map { item ->
            when {
                item is RoutineItem.Single && item.id == exercise.id -> RoutineItem.Single(exercise)
                item is RoutineItem.Group && item.exercises.any { it.id == exercise.id } ->
                    item.copy(exercises = item.exercises.map { if (it.id == exercise.id) exercise else it })
                else -> item
            }
        }
    }

    /** Removes an exercise, a whole circuit, or an exercise from its circuit. */
    fun delete(id: String) = mutateDay { items ->
        items.filterNot { it.id == id }.map { if (it is RoutineItem.Group) it.copy(exercises = it.exercises.filterNot { e -> e.id == id }) else it }
    }

    fun canMove(id: String, offset: Int): Boolean {
        val items = selectedIndex?.let { days[it].items } ?: return false
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) return (i + offset) in items.indices
        val group = items.filterIsInstance<RoutineItem.Group>().firstOrNull { g -> g.exercises.any { it.id == id } } ?: return false
        return (group.exercises.indexOfFirst { it.id == id } + offset) in group.exercises.indices
    }

    /** Moves an item within the day, or an exercise within its circuit. */
    fun move(id: String, offset: Int) {
        if (!canMove(id, offset)) return
        mutateDay { items ->
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) {
                items.toMutableList().apply { add(i + offset, removeAt(i)) }
            } else {
                items.map { item ->
                    if (item is RoutineItem.Group && item.exercises.any { it.id == id }) {
                        val j = item.exercises.indexOfFirst { it.id == id }
                        item.copy(exercises = item.exercises.toMutableList().apply { add(j + offset, removeAt(j)) })
                    } else {
                        item
                    }
                }
            }
        }
    }
}
