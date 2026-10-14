package com.novofitclub.dashboard.feature.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.ThemedField
import com.novofitclub.dashboard.core.ui.cardBackground
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// MARK: Edit ---------------------------------------------------------------

/** "Editar miembro" — edits `wp/v2/member/{id}`, loading its own fresh copy first. */
@Composable
fun MemberEditScreen(
    state: MemberEditState,
    onCancel: () -> Unit,
    onSaved: (MemberProfile) -> Unit,
) {
    FormScaffold(
        title = "Editar miembro",
        onCancel = onCancel,
        saveEnabled = !state.isSaving && !state.isLoading && state.canSave,
        isSaving = state.isSaving,
        onSave = { state.save(onSaved) },
    ) {
        val loadError = state.loadError
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NovoColors.accent)
            }
            loadError != null -> LoadFailedState(loadError)
            else -> FormColumn {
                FormCard {
                    SectionLabel("Nombre")
                    ThemedField(state.name, { state.name = it }, "Nombre completo")
                    SectionLabel("Teléfono", Modifier.padding(top = 4.dp))
                    ThemedField(state.phone, { state.phone = it }, "Teléfono", keyboardType = KeyboardType.Phone)
                    SectionLabel("Correo", Modifier.padding(top = 4.dp))
                    ThemedField(state.email, { state.email = it }, "Correo electrónico", keyboardType = KeyboardType.Email)
                }

                FormCard {
                    DateRow("Fecha de nacimiento", state.birthday) { state.birthday = it }
                    GenderRow(state.gender) { state.gender = it }
                }

                FormCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Estado")
                        Spacer(Modifier.weight(1f))
                        Text(state.status.label, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
                    }
                    Text(
                        "Este campo aún no se puede editar desde la app.",
                        style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                    )
                    DateRow("Fecha de vencimiento", state.membershipExpirationDate) { state.membershipExpirationDate = it }
                    DateRow("Fecha de vencimiento original", state.originalExpirationDate) { state.originalExpirationDate = it }
                    DateRow("Día de pago", state.paymentDay) { state.paymentDay = it }
                    DateRow("Fecha del último pago", state.lastPaymentDate) { state.lastPaymentDate = it }
                }

                FormCard(spacing = 10) {
                    SectionLabel("Tipo de membresía")
                    // A stored value outside the known choices (or none) still shows as its own entry.
                    val current = state.membershipType
                    val extra = if (MembershipTypeOption.all.none { it.id == current }) {
                        listOf(current to (state.membershipTypeLabel ?: "Sin definir"))
                    } else {
                        emptyList()
                    }
                    MembershipTypePicker(
                        selected = current,
                        options = extra + MembershipTypeOption.all.map { it.id to it.label },
                        onSelect = { state.membershipType = it },
                    )
                }

                state.saveError?.let {
                    Text(it, style = NovoFonts.medium(14).copy(color = NovoColors.accent))
                }
            }
        }
    }
}

// MARK: Create -------------------------------------------------------------

/** "Nuevo miembro" — creates a member via `POST wp/v2/member`. */
@Composable
fun MemberCreateScreen(
    state: MemberCreateState,
    vm: MembersViewModel,
    onCancel: () -> Unit,
    onCreated: () -> Unit,
) {
    val draft = state.draft
    // Read live from the list view model, which may still be loading pages in the background.
    val duplicateCedula = vm.memberWithCedula(draft.cedula)
    val duplicateEmail = vm.memberWithEmail(draft.email)

    FormScaffold(
        title = "Nuevo miembro",
        onCancel = onCancel,
        saveEnabled = !state.isSaving && draft.canSave && duplicateCedula == null,
        isSaving = state.isSaving,
        onSave = { state.save(onCreated) },
    ) {
        FormColumn {
            FormCard {
                SectionLabel("Nombre")
                ThemedField(draft.name, { state.draft = state.draft.copy(name = it) }, "Nombre completo")

                SectionLabel("Cédula", Modifier.padding(top = 4.dp))
                ThemedField(draft.cedula, { state.draft = state.draft.copy(cedula = it) }, "Cédula", keyboardType = KeyboardType.Number)
                // Hard block: a cédula is a national ID, so a match is almost certainly a mistake.
                duplicateCedula?.let {
                    Text(
                        "Ya existe un miembro con esta cédula: ${it.name}",
                        style = NovoFonts.body(12).copy(color = NovoColors.accent),
                    )
                }

                SectionLabel("Teléfono", Modifier.padding(top = 4.dp))
                ThemedField(draft.phone, { state.draft = state.draft.copy(phone = it) }, "Teléfono", keyboardType = KeyboardType.Phone)

                SectionLabel("Correo", Modifier.padding(top = 4.dp))
                ThemedField(draft.email, { state.draft = state.draft.copy(email = it) }, "Correo electrónico", keyboardType = KeyboardType.Email)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                    MemberCreateState.emailDomains.forEach { domain ->
                        Text(
                            domain,
                            style = NovoFonts.semibold(12),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(NovoColors.panel)
                                .clickable { state.applyEmailDomain(domain) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                // Warning only: a shared email (e.g. a parent's) is plausible.
                duplicateEmail?.let {
                    Text(
                        "Ya existe un miembro con este correo: ${it.name}",
                        style = NovoFonts.body(12).copy(color = NovoColors.statusExpiring),
                    )
                }
            }

            FormCard {
                DateRow("Fecha de nacimiento", draft.birthday) { state.draft = state.draft.copy(birthday = it) }
                GenderRow(draft.gender) { state.draft = state.draft.copy(gender = it) }
            }

            FormCard(spacing = 10) {
                SectionLabel("Tipo de membresía")
                MembershipTypePicker(
                    selected = draft.membershipType,
                    options = listOf<Pair<String?, String>>(null to "Sin definir") +
                        MembershipTypeOption.all.map { it.id to it.label },
                    onSelect = { state.draft = state.draft.copy(membershipType = it) },
                )
            }

            state.saveError?.let {
                Text(it, style = NovoFonts.medium(14).copy(color = NovoColors.accent))
            }
        }
    }
}

// MARK: Shared form pieces -------------------------------------------------

/** Sheet-style screen: "Cancelar" · centered title · bold "Guardar" (spinner while saving). */
@Composable
private fun FormScaffold(
    title: String,
    onCancel: () -> Unit,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("Cancelar", style = NovoFonts.body(16).copy(color = NovoColors.accent))
            }
            Text(title, style = NovoFonts.semibold(17), modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = NovoColors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(horizontal = 20.dp).size(20.dp),
                    )
                } else {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onSave()
                        },
                        enabled = saveEnabled,
                    ) {
                        Text(
                            "Guardar",
                            style = NovoFonts.semibold(16).copy(
                                color = if (saveEnabled) NovoColors.accent else NovoColors.textSecondary,
                            ),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = NovoColors.separator)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(scroll.isScrollInProgress) {
        if (scroll.isScrollInProgress) focusManager.clearFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        content = content,
    )
}

@Composable
private fun FormCard(spacing: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
        content = content,
    )
}

private val compactDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))

/** Label + compact date chip that opens a Material3 date picker (iOS compact DatePicker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(title: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            date.format(compactDateFormat),
            style = NovoFonts.medium(15).copy(color = NovoColors.accent),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NovoColors.panel)
                .clickable { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val colors = DatePickerDefaults.colors(
            containerColor = NovoColors.card,
            selectedDayContainerColor = NovoColors.accent,
            todayDateBorderColor = NovoColors.accent,
            todayContentColor = NovoColors.accent,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("Aceptar", color = NovoColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = NovoColors.textSecondary) }
            },
            colors = colors,
        ) {
            DatePicker(state = pickerState, colors = colors)
        }
    }
}

/** "Género" with a Masculino/Femenino segmented control (`male` / `female`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("male" to "Masculino", "female" to "Femenino")
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Género", Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow(Modifier.width(200.dp)) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = NovoColors.accent,
                        activeContentColor = Color.White,
                        activeBorderColor = NovoColors.accent,
                        inactiveContainerColor = NovoColors.panel,
                        inactiveContentColor = NovoColors.textPrimary,
                        inactiveBorderColor = NovoColors.separator,
                    ),
                ) {
                    Text(label, style = NovoFonts.semibold(12).copy(color = Color.Unspecified), maxLines = 1)
                }
            }
        }
    }
}

/** Menu-style picker (iOS `.pickerStyle(.menu)`) over `membership_type` choices. */
@Composable
private fun MembershipTypePicker(
    selected: String?,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: "Sin definir"
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            Text(label, style = NovoFonts.medium(16).copy(color = NovoColors.accent), textAlign = TextAlign.Start)
            Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NovoColors.card,
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel,
                            style = NovoFonts.body(15).copy(
                                color = if (value == selected) NovoColors.accent else NovoColors.textPrimary,
                            ),
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
