package com.novofitclub.dashboard.feature.members

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.feature.sales.SearchField
import com.novofitclub.dashboard.feature.sales.SheetTopBar
import com.novofitclub.dashboard.feature.sales.pickers.PickerRowSkeleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate

private data class ProgramOption(val id: Int, val title: String, val date: LocalDate?)

/** "Asignar programa" for the member screen — `novo-staff/v1/programs`, the same list the web dialog searches. */
@Composable
fun ProgramPickerScreen(app: NovoFitClubApp, assignedIds: Set<Int>, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProgramOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onCancel)

    // Restarts on every keystroke; the delay debounces the search.
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(300)
        isLoading = true
        errorMessage = null
        try {
            results = app.api.getArray("novo-staff/v1/programs", mapOf("search" to query)).mapNotNull { record ->
                val id = record.firstNumber("id")?.toInt() ?: return@mapNotNull null
                ProgramOption(id, record.firstString("title") ?: "Programa", MemberOverview.day(record.firstString("date")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
        isLoading = false
    }

    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        SheetTopBar("Asignar programa", onCancel)
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar por nombre",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        val error = errorMessage
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                isLoading && results.isEmpty() -> items(8) { PickerRowSkeleton(showsThumbnail = false) }
                error != null -> item { Message(error) }
                results.isEmpty() -> item { Message("No se encontraron programas.") }
                else -> items(results, key = { it.id }) { program ->
                    val assigned = program.id in assignedIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (assigned) 0.5f else 1f)
                            .clickable(enabled = !assigned) { onPick(program.id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(program.title, style = NovoFonts.medium(15))
                            program.date?.let {
                                Text(it.format(MemberOverview.displayFormat), style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                            }
                        }
                        if (assigned) Text("Ya asignado", style = NovoFonts.body(11).copy(color = NovoColors.textSecondary))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary), modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}
