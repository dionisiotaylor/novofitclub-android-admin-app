package com.novofitclub.dashboard.feature.freezes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.RowDivider
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One congelamiento request. Administrators decide it here, with the same
 * confirmations as the web list (freeze-list.js): Aprobar previews the
 * dates it moves; Anular puts them back and deletes its sale.
 */
@Composable
fun FreezeDetailScreen(app: NovoFitClubApp, initial: Freeze, onBack: () -> Unit, onChanged: () -> Unit) {
    val auth by app.auth.state.collectAsState()
    val scope = rememberCoroutineScope()
    var freeze by remember { mutableStateOf(initial) }
    var pendingAction by remember { mutableStateOf<FreezeAction?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showingEdit by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf<Freeze.Evidence?>(null) }

    val isAdmin = auth.role == "admin"
    // Only while it's pending: once decided, its days are what moved the dates.
    val canEdit = freeze.canEdit && freeze.status == FreezeStatus.Pendiente

    BackHandler(enabled = !showingEdit && fullPhoto == null, onBack = onBack)

    fun run(action: FreezeAction) {
        scope.launch {
            isWorking = true
            errorMessage = null
            try {
                FreezesViewModel.perform(app.api, action, freeze.id)?.let { freeze = it }
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
            }
            isWorking = false
        }
    }

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        ) {
            SectionHeader(freeze.memberName, breadcrumb = "CONGELAMIENTO #${freeze.id}", onBack = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth().cardBackground()) {
                    DetailRow("Estado") { FreezeStatusBadge(freeze.status) }
                    RowDivider()
                    DetailRow("Tipo") { Value(freeze.typeLabel) }
                    RowDivider()
                    DetailRow("Días") { Value(freeze.daysLabel) }
                    RowDivider()
                    DetailRow("Solicitado por") { Value(freeze.requesterName) }
                    freeze.date?.let {
                        RowDivider()
                        DetailRow("Fecha") { Value(Formatters.friendlyDate(it)) }
                    }
                    if (freeze.isArchived) {
                        RowDivider()
                        DetailRow("Archivado") { Value("Sí") }
                    }
                }

                val prevExp = freeze.previousExpiration
                val newExp = freeze.newExpiration
                if (freeze.status == FreezeStatus.Aprobado && newExp != null) {
                    Block("Fechas al aprobar") {
                        Column(Modifier.fillMaxWidth().cardBackground()) {
                            if (prevExp != null) DetailRow("Vencimiento") { DateChange(prevExp, newExp) }
                            val prevPay = freeze.previousPaymentDay
                            val newPay = freeze.newPaymentDay
                            if (prevPay != null && newPay != null) {
                                RowDivider()
                                DetailRow("Día de pago") { DateChange(prevPay, newPay) }
                            }
                        }
                    }
                }

                if (freeze.evidence.isNotEmpty()) {
                    Block("Evidencia") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            freeze.evidence.forEach { photo ->
                                AsyncImage(
                                    model = photo.thumbUrl,
                                    contentDescription = "Evidencia",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NovoColors.card)
                                        .clickable { fullPhoto = photo },
                                )
                            }
                        }
                    }
                }

                if (freeze.notes.isNotEmpty()) {
                    Block("Notas") {
                        Text(freeze.notes, style = NovoFonts.body(14), modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp))
                    }
                }

                Box {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (canEdit) SecondaryButton("Editar solicitud", onClick = { if (!isWorking) showingEdit = true })
                        if (isAdmin) {
                            when (freeze.status) {
                                FreezeStatus.Pendiente -> {
                                    PrimaryButton("Aprobar", onClick = { pendingAction = FreezeAction.Approve }, enabled = !isWorking)
                                    SecondaryButton("Denegar", onClick = { if (!isWorking) pendingAction = FreezeAction.Deny })
                                }
                                FreezeStatus.Aprobado -> SecondaryButton("Anular", onClick = { if (!isWorking) pendingAction = FreezeAction.Void })
                                else -> Unit
                            }
                            if (freeze.status != FreezeStatus.Pendiente && !freeze.isArchived) {
                                SecondaryButton("Archivar", onClick = { if (!isWorking) pendingAction = FreezeAction.Archive })
                            }
                        }
                    }
                    if (isWorking) CircularProgressIndicator(color = NovoColors.accent, modifier = Modifier.align(Alignment.Center))
                }

                errorMessage?.let { Text(it, style = NovoFonts.medium(13).copy(color = NovoColors.accent)) }
            }
        }

        if (showingEdit) {
            OverlayLayer {
                ScopedViewModels {
                    FreezeFormScreen(
                        app = app,
                        freeze = freeze,
                        onDismiss = { showingEdit = false },
                        onSaved = { saved ->
                            freeze = saved
                            onChanged()
                        },
                    )
                }
            }
        }
        fullPhoto?.let { photo ->
            BackHandler { fullPhoto = null }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = photo.url, contentDescription = "Evidencia", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { fullPhoto = null }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            containerColor = NovoColors.card,
            title = { Text(confirmTitle(action), style = NovoFonts.semibold(17)) },
            text = confirmMessage(action, freeze).takeIf { it.isNotEmpty() }?.let { message ->
                { Text(message, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    run(action)
                }) {
                    val destructive = action == FreezeAction.Deny || action == FreezeAction.Void
                    Text(action.button, style = NovoFonts.semibold(15).copy(color = if (destructive) NovoColors.statusExpired else NovoColors.accent))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancelar", style = NovoFonts.body(15).copy(color = NovoColors.accent))
                }
            },
        )
    }
}

// Confirmations — freeze-list.js wording.

private fun confirmTitle(action: FreezeAction) = when (action) {
    FreezeAction.Approve -> "Confirmar aprobación"
    FreezeAction.Deny -> "¿Denegar esta solicitud de congelamiento?"
    FreezeAction.Void -> "¿Anular este congelamiento?"
    FreezeAction.Archive -> "¿Archivar esta solicitud?"
}

private fun confirmMessage(action: FreezeAction, freeze: Freeze): String {
    fun format(date: LocalDate) = date.format(Freeze.displayFormat)
    return when (action) {
        FreezeAction.Approve -> buildList {
            add("¿Aprobar ${freeze.daysLabel} a ${freeze.memberName}?")
            freeze.memberExpiration?.let { add("Vencimiento: ${format(it)} → ${format(freeze.shifted(it))}") }
            freeze.memberPaymentDay?.let { add("Día de pago: ${format(it)} → ${format(freeze.shifted(it))}") }
        }.joinToString("\n")
        FreezeAction.Void -> {
            val back = freeze.previousExpiration?.let { " (vence el ${format(it)})" }.orEmpty()
            "Las fechas de ${freeze.memberName} vuelven a como estaban antes$back y se borra su venta de congelamiento."
        }
        FreezeAction.Deny, FreezeAction.Archive -> ""
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun DetailRow(title: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = NovoFonts.medium(14), textAlign = TextAlign.End)
}

@Composable
private fun DateChange(from: LocalDate, to: LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            from.format(Freeze.displayFormat),
            style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary, textDecoration = TextDecoration.LineThrough),
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(12.dp))
        Text(to.format(Freeze.displayFormat), style = NovoFonts.medium(14))
    }
}
