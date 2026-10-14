package com.novofitclub.dashboard.feature.sales.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SaleThumbnail
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.ThemedField
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.CenteredSpinner
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.RowDivider
import com.novofitclub.dashboard.feature.sales.SegmentedPicker
import com.novofitclub.dashboard.feature.sales.SheetTopBar
import com.novofitclub.dashboard.feature.sales.TextAction
import com.novofitclub.dashboard.feature.sales.UnavailableState
import com.novofitclub.dashboard.feature.sales.edit.SaleEditPayload.PaymentMethod
import com.novofitclub.dashboard.feature.sales.edit.SaleEditPayload.ProductLine
import com.novofitclub.dashboard.feature.sales.pickers.AuthorPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.MemberPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.MembershipPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.ProductCatalogViewModel
import com.novofitclub.dashboard.feature.sales.pickers.ProductPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.SaleMemberSource
import kotlin.math.abs

private enum class ActivePicker { None, Members, Products, Membership, Author }

/**
 * "Nueva venta" / "Editar venta" form (an iOS sheet). The membership sold is
 * only pickable when creating — on an existing sale it's shown read-only,
 * since changing it double-extends the member's expiration server-side.
 * Must be hosted inside [com.novofitclub.dashboard.feature.sales.ScopedViewModels]
 * so each presentation gets a fresh view model.
 */
@Composable
internal fun SaleEditScreen(
    app: NovoFitClubApp,
    saleId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val vm: SaleEditViewModel = viewModel { SaleEditViewModel(app.api, saleId) }
    val catalog: ProductCatalogViewModel = viewModel { ProductCatalogViewModel(app.api) }
    val memberSource: SaleMemberSource = viewModel { SaleMemberSource(app.api) }
    var picker by rememberSaveable { mutableStateOf(ActivePicker.None) }
    var showingDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        Column(Modifier.fillMaxSize()) {
            val payload = vm.payload
            SheetTopBar(
                title = if (vm.isCreating) "Nueva venta" else "Editar venta",
                onCancel = onDismiss,
            ) {
                if (!vm.isCreating) {
                    IconButton(
                        onClick = { showingDeleteConfirmation = true },
                        enabled = !vm.isDeleting && !vm.isSaving,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = NovoColors.accent.copy(alpha = if (vm.isDeleting || vm.isSaving) 0.4f else 1f),
                        )
                    }
                }
                if (vm.isSaving) {
                    CircularProgressIndicator(
                        color = NovoColors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(horizontal = 16.dp).size(20.dp),
                    )
                } else {
                    TextAction(
                        "Guardar",
                        bold = true,
                        enabled = !vm.isDeleting && (payload?.canSave ?: false),
                        onClick = {
                            vm.save {
                                onSaved()
                                onDismiss()
                            }
                        },
                    )
                }
            }

            val loadError = vm.loadError
            when {
                vm.isLoading -> CenteredSpinner()
                loadError != null -> UnavailableState(loadError, onRetry = vm::load)
                payload != null -> EditForm(
                    vm = vm,
                    payload = payload,
                    onPick = { picker = it },
                )
            }
        }

        val payload = vm.payload
        if (payload != null && picker != ActivePicker.None) {
            val close = { picker = ActivePicker.None }
            OverlayLayer {
                when (picker) {
                    ActivePicker.Members -> MemberPickerScreen(
                        source = memberSource,
                        alreadySelected = payload.members,
                        onDone = { selected ->
                            vm.update { it.copy(members = selected) }
                            close()
                        },
                        onCancel = close,
                    )
                    ActivePicker.Products -> ProductPickerScreen(
                        catalog = catalog,
                        alreadySelectedIds = payload.physicalProducts.map { it.productId }.toSet(),
                        onDone = { added ->
                            vm.update { current ->
                                current.copy(
                                    physicalProducts = current.physicalProducts + added.map { item ->
                                        ProductLine(item.id, 1, item.title, item.price, item.imageUrl)
                                    },
                                )
                            }
                            close()
                        },
                        onCancel = close,
                    )
                    ActivePicker.Membership -> MembershipPickerScreen(
                        catalog = catalog,
                        onSelect = { item ->
                            vm.update { it.withMembership(item) }
                            close()
                        },
                        onCancel = close,
                    )
                    ActivePicker.Author -> AuthorPickerScreen(
                        api = app.api,
                        onSelect = { id, name ->
                            vm.update { it.copy(authorId = id, authorName = name) }
                            close()
                        },
                        onCancel = close,
                    )
                    ActivePicker.None -> Unit
                }
            }
        }
    }

    if (showingDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showingDeleteConfirmation = false },
            containerColor = NovoColors.card,
            title = { Text("¿Eliminar esta venta?", style = NovoFonts.semibold(17)) },
            text = {
                Text(
                    "Se moverá a la papelera de wp-admin — se puede restaurar desde ahí si fue un error.",
                    style = NovoFonts.body(14).copy(color = NovoColors.textSecondary),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showingDeleteConfirmation = false
                    vm.delete {
                        onDismiss()
                        onDeleted()
                    }
                }) {
                    Text("Eliminar", style = NovoFonts.semibold(15).copy(color = NovoColors.statusExpired))
                }
            },
            dismissButton = {
                TextButton(onClick = { showingDeleteConfirmation = false }) {
                    Text("Cancelar", style = NovoFonts.body(15).copy(color = NovoColors.accent))
                }
            },
        )
    }
}

@Composable
private fun EditForm(
    vm: SaleEditViewModel,
    payload: SaleEditPayload,
    onPick: (ActivePicker) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        MembershipSection(vm.isCreating, payload, onPick = { onPick(ActivePicker.Membership) }, onClear = {
            vm.update { it.clearingMembership() }
        })

        MembersSection(payload, onAdd = { onPick(ActivePicker.Members) }, onRemove = { id ->
            vm.update { p -> p.copy(members = p.members.filterNot { it.memberPostId == id }) }
        })

        ProductsSection(
            title = "Productos",
            lines = payload.physicalProducts,
            onAdd = { onPick(ActivePicker.Products) },
            onChange = { lines -> vm.update { it.copy(physicalProducts = lines) } },
        )
        if (payload.services.isNotEmpty()) {
            ProductsSection(
                title = "Servicios",
                lines = payload.services,
                onAdd = null,
                onChange = { lines -> vm.update { it.copy(services = lines) } },
            )
        }

        PaymentSection(payload) { transform -> vm.update(transform) }
        NotesSection(payload) { transform -> vm.update(transform) }
        StatusSection(payload) { status -> vm.update { it.copy(status = status) } }
        if (!vm.isCreating) {
            AuthorSection(payload, onPick = { onPick(ActivePicker.Author) })
        }

        vm.saveError?.let { Text(it, style = NovoFonts.medium(14).copy(color = NovoColors.accent)) }
        vm.deleteError?.let { Text(it, style = NovoFonts.medium(14).copy(color = NovoColors.accent)) }
    }
}

// region Shared bits

@Composable
private fun AddButton(text: String, onClick: () -> Unit, size: Int = 13) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Icon(Icons.Filled.AddCircle, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size((size + 5).dp))
        Text(text, style = NovoFonts.semibold(size).copy(color = NovoColors.accent))
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Icon(
        Icons.Filled.Cancel,
        contentDescription = "Quitar",
        tint = NovoColors.textSecondary,
        modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClick),
    )
}

@Composable
private fun SectionHeaderRow(title: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SectionLabel(title)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
}

/** iOS `Picker(...).pickerStyle(.menu)`: current value + caret opening a menu. */
@Composable
private fun <T> MenuPicker(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            Text(label(selected), style = NovoFonts.medium(15).copy(color = NovoColors.accent))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = NovoColors.card) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            style = NovoFonts.medium(15).copy(color = if (option == selected) NovoColors.accent else NovoColors.textPrimary),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

// endregion

// region Membership

@Composable
private fun MembershipSection(isCreating: Boolean, payload: SaleEditPayload, onPick: () -> Unit, onClear: () -> Unit) {
    val label = payload.membershipLabel
    if (isCreating) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Membresía")
            if (label != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = NovoFonts.semibold(15))
                        Text(
                            Formatters.money(payload.membershipPrice) ?: "—",
                            style = NovoFonts.semibold(14).copy(color = NovoColors.accent),
                        )
                    }
                    RemoveButton(onClear)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().cardBackground().clickable(onClick = onPick).padding(16.dp),
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(19.dp))
                    Text("Seleccionar membresía", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
                }
            }
        }
    } else if (label != null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
        ) {
            SectionLabel("Membresía")
            Text(label, style = NovoFonts.semibold(16))
            Text(Formatters.money(payload.membershipPrice) ?: "—", style = NovoFonts.semibold(15).copy(color = NovoColors.accent))
            Text("Este campo aún no se puede editar desde la app.", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
        }
    }
}

// endregion

// region Members

@Composable
private fun MembersSection(payload: SaleEditPayload, onAdd: () -> Unit, onRemove: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderRow("Miembros") { AddButton("Agregar", onAdd) }
        if (payload.members.isEmpty()) {
            EmptyHint("Sin miembros asociados.")
        } else {
            Column(Modifier.fillMaxWidth().cardBackground()) {
                payload.members.forEachIndexed { index, member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(member.displayName, style = NovoFonts.medium(15), modifier = Modifier.weight(1f))
                        RemoveButton { onRemove(member.memberPostId) }
                    }
                    if (index < payload.members.size - 1) RowDivider()
                }
            }
        }
    }
}

// endregion

// region Products / services

@Composable
private fun ProductsSection(
    title: String,
    lines: List<ProductLine>,
    onAdd: (() -> Unit)?,
    onChange: (List<ProductLine>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderRow(title) { if (onAdd != null) AddButton("Agregar", onAdd) }
        if (lines.isEmpty()) {
            EmptyHint("Sin productos.")
        } else {
            Column(Modifier.fillMaxWidth().cardBackground()) {
                lines.forEachIndexed { index, line ->
                    ProductRow(
                        line = line,
                        onQuantity = { qty -> onChange(lines.map { if (it.productId == line.productId) it.copy(quantity = qty) else it }) },
                        onRemove = { onChange(lines.filterNot { it.productId == line.productId }) },
                    )
                    if (index < lines.size - 1) RowDivider()
                }
            }
        }
    }
}

@Composable
private fun ProductRow(line: ProductLine, onQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SaleThumbnail(line.imageUrl, size = 36.dp, cornerRadius = 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.title, style = NovoFonts.medium(14))
            line.price?.let {
                Text(Formatters.money(it * line.quantity) ?: "—", style = NovoFonts.body(12).copy(color = NovoColors.accent))
            }
        }
        Stepper(value = line.quantity, range = 1..99, onChange = onQuantity)
        RemoveButton(onRemove)
    }
}

/** iOS `Stepper(value:in:)` with the value shown between − and +. */
@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", enabled = value > range.first) { onChange(value - 1) }
        Text(
            "$value",
            style = NovoFonts.semibold(14),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        StepperButton("+", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NovoColors.panel)
            .border(1.dp, NovoColors.separator, RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(symbol, style = NovoFonts.semibold(16))
    }
}

// endregion

// region Payment

@Composable
private fun PaymentSection(payload: SaleEditPayload, update: ((SaleEditPayload) -> SaleEditPayload) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeaderRow("Pago") {
            Switch(
                checked = payload.isMultiplePayment,
                onCheckedChange = { checked -> update { it.copy(isMultiplePayment = checked) } },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NovoColors.accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = NovoColors.panel,
                    uncheckedThumbColor = NovoColors.textSecondary,
                    uncheckedBorderColor = NovoColors.separator,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text("Combinado", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
        }

        if (payload.isMultiplePayment) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
            ) {
                payload.paymentLegs.forEach { leg ->
                    PaymentLegRow(
                        leg = leg,
                        onChange = { changed ->
                            update { p -> p.copy(paymentLegs = p.paymentLegs.map { if (it.id == leg.id) changed else it }) }
                        },
                        onRemove = { update { p -> p.copy(paymentLegs = p.paymentLegs.filterNot { it.id == leg.id }) } },
                    )
                }
                AddButton("Agregar método", { update { it.addingPaymentLeg() } })

                val total = payload.computedTotal
                val legsTotal = payload.paymentLegsTotal
                val color = if (abs(legsTotal - total) < 1) NovoColors.statusActive else NovoColors.accent
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "Total de la venta: ${Formatters.money(total) ?: "—"}",
                        style = NovoFonts.body(12).copy(color = color),
                        modifier = Modifier.weight(1f),
                    )
                    Text("Suma: ${Formatters.money(legsTotal) ?: "—"}", style = NovoFonts.body(12).copy(color = color))
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
            ) {
                SegmentedPicker(
                    options = PaymentMethod.entries,
                    selected = payload.singlePayment,
                    label = { it.label },
                    onSelect = { method -> update { it.copy(singlePayment = method) } },
                )
                if (payload.singlePayment == PaymentMethod.Card) {
                    ThemedField(
                        value = payload.singleCardNumber,
                        onValueChange = { text -> update { it.copy(singleCardNumber = text) } },
                        placeholder = "Últimos dígitos de la tarjeta",
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentLegRow(
    leg: SaleEditPayload.PaymentLeg,
    onChange: (SaleEditPayload.PaymentLeg) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MenuPicker(
                selected = leg.method,
                options = PaymentMethod.entries,
                label = { it.label },
                onSelect = { onChange(leg.copy(method = it)) },
            )
            Spacer(Modifier.weight(1f))
            RemoveButton(onRemove)
        }
        ThemedField(
            value = leg.amountText,
            onValueChange = { onChange(leg.copy(amountText = it)) },
            placeholder = "Monto",
            keyboardType = KeyboardType.Number,
        )
        if (leg.method == PaymentMethod.Card) {
            ThemedField(
                value = leg.cardNumber,
                onValueChange = { onChange(leg.copy(cardNumber = it)) },
                placeholder = "Últimos dígitos de la tarjeta",
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

// endregion

// region Notes

@Composable
private fun NotesSection(payload: SaleEditPayload, update: ((SaleEditPayload) -> SaleEditPayload) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderRow("Notas") { AddButton("Agregar", { update { it.addingNote() } }) }
        if (payload.notes.isEmpty()) {
            EmptyHint("Sin notas.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                payload.notes.forEach { note ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedField(
                            value = note.text,
                            onValueChange = { text ->
                                update { p -> p.copy(notes = p.notes.map { if (it.id == note.id) it.copy(text = text) else it }) }
                            },
                            placeholder = "Nota",
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.padding(top = 16.dp)) {
                            RemoveButton { update { p -> p.copy(notes = p.notes.filterNot { it.id == note.id }) } }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Status / author

@Composable
private fun StatusSection(payload: SaleEditPayload, onSelect: (SaleEditPayload.SaleStatus) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
    ) {
        SectionLabel("Estado")
        MenuPicker(
            selected = payload.status,
            options = SaleEditPayload.SaleStatus.entries,
            label = { it.label },
            onSelect = onSelect,
        )
    }
}

/**
 * Only takes effect when the authenticated user is an administrator — the
 * author picker itself is the gate (its endpoint 403s for anyone else).
 */
@Composable
private fun AuthorSection(payload: SaleEditPayload, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Atendida por")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().cardBackground().clickable(onClick = onPick).padding(16.dp),
        ) {
            Text(payload.authorName ?: "Seleccionar", style = NovoFonts.medium(15), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

// endregion
