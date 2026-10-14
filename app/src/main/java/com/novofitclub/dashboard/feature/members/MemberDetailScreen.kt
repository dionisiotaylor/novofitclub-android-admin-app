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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SaleThumbnail
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.util.Formatters

/** Miembro detail: hero, contact info and that member's full sales history. */
@Composable
fun MemberDetailScreen(
    state: MemberDetailState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSale: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        Column(Modifier.fillMaxSize()) {
            NavBar(state, onBack, onEdit)
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                Hero(state)
                ContactCard(state)
                SalesSection(state, onOpenSale)
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
private fun Hero(state: MemberDetailState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(state.member.avatarColor),
        ) {
            Text(initials(state.displayName), style = NovoFonts.semibold(19))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(state.displayName, style = NovoFonts.semibold(18), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Cédula ${state.member.memberId}", style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
            Text(state.displayStatusLabel, style = NovoFonts.semibold(13).copy(color = state.displayStatus.color))
        }
    }
}

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
        Formatters.money(sale.amount)?.let {
            Text(it, style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
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
