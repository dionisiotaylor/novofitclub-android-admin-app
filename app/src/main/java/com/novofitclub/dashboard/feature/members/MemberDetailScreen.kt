package com.novofitclub.dashboard.feature.members

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SaleThumbnail
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.freezes.FreezeFormScreen
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.SaleTag
import com.novofitclub.dashboard.feature.sales.SaleTagsView
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import com.novofitclub.dashboard.feature.sales.edit.SaleEditPayload
import com.novofitclub.dashboard.feature.sales.saleAmountColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Miembro detail screen, laid out like the wp-admin member screen
 * (themes/.../app/members/edit-screen.php): a hero with status, plan type
 * and expiry, then cards — contact, Membresía (último pago → vence),
 * Programas (newest = "Actual"), Ventas and the change history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    app: NovoFitClubApp,
    state: MemberDetailState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSale: (String) -> Unit,
) {
    val auth by app.auth.state.collectAsState()
    var showingProgramPicker by remember { mutableStateOf(false) }
    var programToRemove by remember { mutableStateOf<MemberOverview.Program?>(null) }
    var showingFreezeForm by remember { mutableStateOf(false) }
    var freezeRequested by remember { mutableStateOf(false) }
    var showsAllHistory by remember { mutableStateOf(false) }
    val overview = state.overview.overview

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        Column(Modifier.fillMaxSize()) {
            NavBar(state, onBack, onEdit)
            PullToRefreshBox(
                isRefreshing = state.overview.isLoading && overview != null,
                onRefresh = state::reload,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                ) {
                    Hero(state, overview)
                    ContactCard(state)
                    val overviewError = state.overview.errorMessage
                    if (overviewError != null && overview == null) {
                        Text(overviewError, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
                    }
                    if (overview != null) {
                        MembershipCard(overview, canFreeze = auth.can.requestFreezes, freezeRequested = freezeRequested) { showingFreezeForm = true }
                        ProgramsCard(state.overview, overview, onAssign = { showingProgramPicker = true }, onRemove = { programToRemove = it })
                    }
                    SalesSection(state, onOpenSale)
                    if (overview != null) HistoryCard(overview, showsAllHistory) { showsAllHistory = true }
                }
            }
        }

        AnimatedVisibility(
            visible = state.showSavedToast,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 54.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(NovoColors.card)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NovoColors.statusActive, modifier = Modifier.size(18.dp))
                Text("Cambios guardados", style = NovoFonts.semibold(14))
            }
        }

        if (showingProgramPicker) {
            OverlayLayer {
                ProgramPickerScreen(
                    app = app,
                    assignedIds = overview?.programs?.map { it.id }?.toSet().orEmpty(),
                    onPick = { id ->
                        showingProgramPicker = false
                        state.overview.assignProgram(id)
                    },
                    onCancel = { showingProgramPicker = false },
                )
            }
        }
        if (showingFreezeForm) {
            OverlayLayer {
                ScopedViewModels {
                    FreezeFormScreen(
                        app = app,
                        freeze = null,
                        member = state.member.id.toIntOrNull()?.let { SaleEditPayload.MemberRef(it, state.displayName) },
                        onDismiss = { showingFreezeForm = false },
                        onSaved = { freezeRequested = true },
                    )
                }
            }
        }
    }

    programToRemove?.let { program ->
        AlertDialog(
            onDismissRequest = { programToRemove = null },
            containerColor = NovoColors.card,
            title = { Text("¿Quitar este programa?", style = NovoFonts.semibold(17)) },
            text = { Text(program.title, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary)) },
            confirmButton = {
                TextButton(onClick = {
                    programToRemove = null
                    state.overview.removeProgram(program.id)
                }) { Text("Quitar", style = NovoFonts.semibold(15).copy(color = NovoColors.statusExpired)) }
            },
            dismissButton = {
                TextButton(onClick = { programToRemove = null }) {
                    Text("Cancelar", style = NovoFonts.body(15).copy(color = NovoColors.accent))
                }
            },
        )
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).clip(CircleShape).background(NovoColors.card).clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = NovoColors.textPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NavBar(state: MemberDetailState, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)
        Text(
            "MIEMBRO #${state.member.memberId}",
            style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val color = state.displayStatus.color
        Text(
            state.displayStatus.word,
            style = NovoFonts.semibold(12).copy(color = color),
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        CircleIconButton(Icons.Outlined.Edit, "Editar miembro", onEdit)
    }
}

@Composable
private fun Hero(state: MemberDetailState, overview: MemberOverview?) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
    ) {
        MemberAvatar(initials(state.displayName), state.member.avatarColor, state.member.photoUrl, 64.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Text(state.displayName, style = NovoFonts.title(20), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("Cédula ${state.member.memberId}", overview?.age?.let { "$it años" }).joinToString(" · "),
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(state.displayStatus.word, state.displayStatus.color, dot = true)
                overview?.typeLabel?.let { Chip(it, overview.typeColor ?: NovoColors.textSecondary, dot = false) }
            }
            val expiration = overview?.expiration
            if (expiration != null) {
                ExpiryLine(expiration)
            } else if (overview == null) {
                Text(state.displayStatusLabel, style = NovoFonts.semibold(13).copy(color = state.displayStatus.color))
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, dot: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = NovoFonts.semibold(12).copy(color = color), maxLines = 1)
    }
}

/** "Vence el 10/11/2026 · en 16 días" — the web hero's wording. */
@Composable
private fun ExpiryLine(expiration: LocalDate) {
    val days = MemberOverview.daysFromToday(expiration)
    val date = expiration.format(MemberOverview.displayFormat)
    val text = when {
        days > 0 -> "Vence el $date · en ${if (days == 1) "1 día" else "$days días"}"
        days == 0 -> "Vence hoy, $date"
        else -> "Venció el $date · hace ${if (days == -1) "1 día" else "${-days} días"}"
    }
    Text(text, style = NovoFonts.medium(13).copy(color = if (days < 0) NovoColors.statusExpired else NovoColors.textPrimary))
}

@Composable
private fun CardHeader(title: String, icon: ImageVector, action: Pair<String, () -> Unit>? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(14.dp))
        Text(
            "  $title",
            style = NovoFonts.semibold(12).copy(color = NovoColors.textSecondary, letterSpacing = 0.5.sp),
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = action.second).padding(4.dp),
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(18.dp))
                Text(action.first, style = NovoFonts.semibold(13).copy(color = NovoColors.accent))
            }
        }
    }
}

// region Membresía

/** Último pago → vence as a bar of the days used up (nfc_member_render_progress()). */
@Composable
private fun MembershipCard(overview: MemberOverview, canFreeze: Boolean, freezeRequested: Boolean, onFreeze: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardHeader("MEMBRESÍA", Icons.Outlined.CreditCard, if (canFreeze) "Congelar" to onFreeze else null)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp)) {
            overview.expiration?.let { MembershipProgress(overview.lastPayment, it) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateLine("Día de pago", overview.paymentDay)
                DateLine("Vencimiento original", overview.originalExpiration)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.padding(top = 1.dp).size(13.dp))
                Text(
                    "Las fechas las cambia una venta, un congelamiento aprobado o administración.",
                    style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                )
            }
            if (freezeRequested) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NovoColors.statusActive, modifier = Modifier.padding(top = 1.dp).size(13.dp))
                    Text(
                        "Solicitud de congelamiento enviada. Administración la revisa.",
                        style = NovoFonts.medium(12).copy(color = NovoColors.statusActive),
                    )
                }
            }
        }
    }
}

@Composable
private fun MembershipProgress(lastPayment: LocalDate?, expiration: LocalDate) {
    val left = MemberOverview.daysFromToday(expiration)
    val total = if (lastPayment != null && lastPayment < expiration) ChronoUnit.DAYS.between(lastPayment, expiration).toInt() else 0
    val used = if (total > 0) ((total - maxOf(0, left)).toFloat() / total).coerceIn(0f, 1f) else if (left < 0) 1f else 0f
    val remaining = when {
        left < 0 -> "Venció hace ${if (-left == 1) "1 día" else "${-left} días"}"
        total > 0 -> "Quedan $left de $total días"
        else -> "Quedan ${if (left == 1) "1 día" else "$left días"}"
    }
    val barColor = when {
        left < 0 -> NovoColors.statusExpired
        left <= 7 -> NovoColors.statusExpiring
        else -> NovoColors.statusActive
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("Último pago")
                Text(lastPayment?.format(MemberOverview.displayFormat) ?: "—", style = NovoFonts.semibold(14))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("Vence")
                Text(expiration.format(MemberOverview.displayFormat), style = NovoFonts.semibold(14))
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(used).fillMaxHeight().clip(CircleShape).background(barColor))
        }
        Text(remaining, style = NovoFonts.medium(13).copy(color = if (left < 0) NovoColors.statusExpired else NovoColors.textPrimary))
    }
}

@Composable
private fun DateLine(title: String, date: LocalDate?) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary), modifier = Modifier.weight(1f))
        Text(date?.format(MemberOverview.displayFormat) ?: "—", style = NovoFonts.medium(14))
    }
}

// endregion

// region Programas

@Composable
private fun ProgramsCard(
    state: MemberOverviewState,
    overview: MemberOverview,
    onAssign: () -> Unit,
    onRemove: (MemberOverview.Program) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardHeader("PROGRAMAS", Icons.Outlined.FitnessCenter, "Asignar" to onAssign)
        if (overview.programs.isEmpty()) {
            Text(
                "Sin programas asignados.",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
            )
        } else {
            val programs = overview.programsNewestFirst
            Column(Modifier.fillMaxWidth().cardBackground()) {
                programs.forEachIndexed { index, program ->
                    ProgramRow(program, busy = state.busyProgramId == program.id) { onRemove(program) }
                    if (index < programs.lastIndex) HorizontalDivider(color = NovoColors.separator)
                }
            }
        }
        state.programError?.let { Text(it, style = NovoFonts.medium(12).copy(color = NovoColors.accent)) }
    }
}

@Composable
private fun ProgramRow(program: MemberOverview.Program, busy: Boolean, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val tint = if (program.isCurrent) NovoColors.statusActive else NovoColors.textSecondary
        Box(Modifier.width(78.dp)) {
            Text(
                if (program.isCurrent) "ACTUAL" else "ANTERIOR",
                style = NovoFonts.semibold(10).copy(color = tint),
                modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                program.title,
                style = NovoFonts.medium(14).copy(color = if (program.isCurrent) NovoColors.textPrimary else NovoColors.textSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            program.date?.let {
                Text(it.format(MemberOverview.displayFormat), style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
        }
        if (busy) {
            CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Quitar programa",
                tint = NovoColors.textSecondary,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onRemove),
            )
        }
    }
}

// endregion

// region Historial

@Composable
private fun HistoryCard(overview: MemberOverview, showsAll: Boolean, onShowAll: () -> Unit) {
    val entries = if (showsAll) overview.history else overview.history.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardHeader("HISTORIAL DE CAMBIOS", Icons.Outlined.History)
        Column(Modifier.fillMaxWidth().cardBackground()) {
            if (overview.history.isEmpty()) {
                val since = overview.historySince?.let { " desde el ${it.format(MemberOverview.displayFormat)}" }.orEmpty()
                Text(
                    "Todavía no hay cambios registrados$since.",
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    HistoryRow(entry)
                    if (index < entries.lastIndex) HorizontalDivider(color = NovoColors.separator)
                }
                if (overview.history.size > entries.size) {
                    HorizontalDivider(color = NovoColors.separator)
                    Text(
                        "Ver más cambios",
                        style = NovoFonts.semibold(13).copy(color = NovoColors.accent),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onShowAll).padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: MemberOverview.HistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.actor, style = NovoFonts.semibold(13))
            entry.source?.let {
                Text(
                    it,
                    style = NovoFonts.semibold(10).copy(color = NovoColors.textSecondary),
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            entry.date?.let { Text(MemberOverview.whenLabel(it), style = NovoFonts.body(11).copy(color = NovoColors.textSecondary)) }
        }
        if (entry.isCreation) {
            Text("Creó la ficha.", style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
        } else {
            entry.changes.forEach { Text(changeText(it), style = NovoFonts.body(13)) }
        }
    }
}

/** "Vencimiento: ~~05/11/2026~~ → 05/12/2026". */
private fun changeText(change: MemberOverview.HistoryChange): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = NovoColors.textSecondary)) { append("${change.label}: ") }
    if (change.isHidden) {
        withStyle(SpanStyle(color = NovoColors.textPrimary, fontStyle = FontStyle.Italic)) { append("cambió") }
        return@buildAnnotatedString
    }
    withStyle(SpanStyle(color = NovoColors.textSecondary, textDecoration = TextDecoration.LineThrough)) {
        append(change.from?.ifEmpty { null } ?: "vacío")
    }
    withStyle(SpanStyle(color = NovoColors.textSecondary)) { append(" → ") }
    withStyle(SpanStyle(color = NovoColors.textPrimary)) { append(change.to?.ifEmpty { null } ?: "vacío") }
}

// endregion

@Composable
private fun ContactCard(state: MemberDetailState) {
    val phone = state.displayPhone
    val email = state.displayEmail
    Column(Modifier.fillMaxWidth().cardBackground()) {
        if (phone != null) ContactRow(Icons.Outlined.Phone, phone)
        if (phone != null && email != null) {
            HorizontalDivider(color = NovoColors.separator, modifier = Modifier.padding(start = 64.dp))
        }
        if (email != null) ContactRow(Icons.Outlined.Email, email)
        if (phone == null && email == null) ContactRow(Icons.AutoMirrored.Outlined.HelpOutline, "Sin información de contacto")
    }
}

@Composable
private fun ContactRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)),
        ) {
            Icon(icon, contentDescription = null, tint = NovoColors.textPrimary, modifier = Modifier.size(18.dp))
        }
        Text(text, style = NovoFonts.medium(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SalesSection(state: MemberDetailState, onOpenSale: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "VENTAS",
                style = NovoFonts.semibold(12).copy(color = NovoColors.textSecondary, letterSpacing = 0.5.sp),
            )
            Spacer(Modifier.weight(1f))
            if (state.sales.isNotEmpty()) {
                val count = state.sales.size
                Text(
                    "$count venta${if (count == 1) "" else "s"} · ${Formatters.money(state.totalAmount) ?: "—"}",
                    style = NovoFonts.semibold(12).copy(color = NovoColors.textSecondary),
                )
            }
        }

        val error = state.errorMessage
        when {
            state.isLoading && state.sales.isEmpty() -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) {
                CircularProgressIndicator(color = NovoColors.accent)
            }

            error != null && state.sales.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                Text("No se pudieron cargar las ventas", style = NovoFonts.semibold(14))
                Text(
                    error,
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    textAlign = TextAlign.Center,
                )
            }

            state.sales.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(vertical = 32.dp),
            ) {
                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(28.dp))
                Text("Sin ventas registradas", style = NovoFonts.semibold(14))
            }

            else -> {
                Column(Modifier.fillMaxWidth().cardBackground()) {
                    state.sales.forEachIndexed { index, sale ->
                        MemberSaleRow(sale, onClick = { onOpenSale(sale.id) })
                        if (index < state.sales.lastIndex) {
                            HorizontalDivider(color = NovoColors.separator, modifier = Modifier.padding(start = 68.dp))
                        }
                    }
                }
                if (state.isLoadingMore) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberSaleRow(sale: MemberSale, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SaleThumbnail(sale.imageUrl, size = 40.dp, cornerRadius = 10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(sale.productTitle, style = NovoFonts.semibold(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
            sale.dateLabel?.let {
                Text(it, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SaleTagsView(SaleTag.of(sale.isCanje, sale.hasAjuste))
            Formatters.money(sale.amount)?.let {
                Text(it, style = NovoFonts.semibold(14).copy(color = saleAmountColor(sale.isCanje, sale.hasAjuste)))
            }
        }
        Spacer(Modifier.width(0.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NovoColors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}
