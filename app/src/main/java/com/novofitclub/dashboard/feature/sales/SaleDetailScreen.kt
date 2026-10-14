package com.novofitclub.dashboard.feature.sales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.R
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SaleThumbnail
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.edit.SaleEditScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Self-contained sale detail screen (including its edit/delete flows), also
 * pushed by the Members tab from a member's sales list. [onChanged] runs after
 * a successful edit or delete; after a delete the screen also calls [onBack]
 * itself, like iOS dismissing the detail view.
 */
@Composable
fun SaleDetailScreen(app: NovoFitClubApp, saleId: String, onBack: () -> Unit, onChanged: () -> Unit) {
    key(saleId) {
        ScopedViewModels {
            SaleDetailContent(
                app = app,
                saleId = saleId,
                onBack = onBack,
                onEdited = onChanged,
                onDeleted = {
                    onChanged()
                    onBack()
                },
            )
        }
    }
}

/**
 * The `wp/v2/sale` list only carries generic WordPress post fields. The real
 * sale details live behind `sale-creator/v1/sales/{id}`, so this screen
 * fetches them separately when opened.
 */
class SaleDetailViewModel(private val api: WPApiClient, private val saleId: String) : ViewModel() {
    var detail by mutableStateOf<SaleDetail?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val fields = api.getObject("sale-creator/v1/sales/$saleId")
                detail = SaleDetail(saleId, fields)
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error desconocido."
            }
            isLoading = false
        }
    }

    fun retry() {
        isLoading = true
        errorMessage = null
        load()
    }
}

@Composable
internal fun SaleDetailContent(
    app: NovoFitClubApp,
    saleId: String,
    onBack: () -> Unit,
    onEdited: () -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: SaleDetailViewModel = viewModel { SaleDetailViewModel(app.api, saleId) }
    var showingEdit by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        val detail = vm.detail
        val error = vm.errorMessage
        when {
            vm.isLoading -> CenteredSpinner()
            error != null -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(start = 20.dp, top = 8.dp)) {
                    CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)
                }
                UnavailableState(error, onRetry = vm::retry)
            }
            detail != null -> DetailBody(detail, onBack = onBack, onEdit = { showingEdit = true })
        }

        if (showingEdit) {
            OverlayLayer {
                ScopedViewModels {
                    SaleEditScreen(
                        app = app,
                        saleId = saleId,
                        onDismiss = { showingEdit = false },
                        onSaved = {
                            vm.load()
                            onEdited()
                        },
                        onDeleted = onDeleted,
                    )
                }
            }
        }
    }
}

/**
 * The image anchors the top as a big, full-width backdrop with the sale info
 * overlapping in front of it in a rounded card; it drifts at half speed as
 * the content scrolls (a light take on iOS's stretchy parallax header).
 */
@Composable
private fun DetailBody(detail: SaleDetail, onBack: () -> Unit, onEdit: () -> Unit) {
    val headerHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .graphicsLayer { translationY = scroll.value * 0.5f },
            ) {
                HeroImage(detail.imageUrl, Modifier.fillMaxSize())
                // Faint scrim so the back button/status pill stay legible.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
                )
            }
            InfoCard(
                detail,
                Modifier
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            )
        }
        NavBar(detail, onBack, onEdit)
    }
}

@Composable
private fun NavBar(detail: SaleDetail, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)
        Text(
            "VENTA #${detail.id}",
            style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp),
        )
        Spacer(Modifier.weight(1f))
        StatusPill(detail.statusLabel, detail.statusColor)
        CircleIconButton(Icons.Outlined.Edit, "Editar", onEdit, iconSize = 15.dp)
    }
}

/**
 * The API hands back WordPress's small generated thumbnail (e.g.
 * "...-150x150.jpg"), which looks blurry stretched across this header.
 * WordPress keeps the original upload at the same path minus the "-WxH"
 * suffix, so this tries that first and falls back to the thumbnail, then to
 * the generic placeholder.
 */
@Composable
private fun HeroImage(url: String?, modifier: Modifier) {
    val placeholder = @Composable {
        Image(
            painterResource(R.drawable.generic_product_thumb),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
    if (url.isNullOrEmpty()) {
        placeholder()
        return
    }
    val fullRes = fullResolutionUrl(url)
    val thumbnail = @Composable {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { Box(modifier.background(NovoColors.card)) },
            error = { placeholder() },
            modifier = modifier,
        )
    }
    if (fullRes == url) {
        thumbnail()
    } else {
        SubcomposeAsyncImage(
            model = fullRes,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { Box(modifier.background(NovoColors.card)) },
            error = { thumbnail() },
            modifier = modifier,
        )
    }
}

private val sizeSuffix = Regex("-\\d+x\\d+$")

private fun fullResolutionUrl(url: String): String {
    val dot = url.lastIndexOf('.')
    if (dot < 0) return url
    val base = url.substring(0, dot)
    val ext = url.substring(dot)
    val match = sizeSuffix.find(base) ?: return url
    return base.removeRange(match.range) + ext
}

@Composable
private fun InfoCard(detail: SaleDetail, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(NovoColors.background)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
    ) {
        HeroText(detail)

        if (detail.members.isNotEmpty()) {
            DetailSection("Miembros") {
                Column(Modifier.fillMaxWidth().cardBackground()) {
                    detail.members.forEachIndexed { index, member ->
                        MemberRow(member)
                        if (index < detail.members.size - 1) RowDivider(Modifier.padding(start = 68.dp))
                    }
                }
            }
        }

        if (detail.payments.isNotEmpty()) {
            DetailSection("Pago") { PaymentCard(detail.payments) }
        }

        detail.author?.let { author ->
            DetailSection("Atendida por") { IconRow(Icons.Outlined.Person, author) }
        }

        if (detail.physicalProducts.isNotEmpty()) {
            DetailSection("Productos") { LineItemsCard(detail.physicalProducts) }
        }

        if (detail.services.isNotEmpty()) {
            DetailSection("Servicios") { LineItemsCard(detail.services) }
        }

        if (detail.notes.isNotEmpty()) {
            DetailSection("Notas") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
                ) {
                    detail.notes.forEach { Text(it, style = NovoFonts.body(14)) }
                }
            }
        }
    }
}

@Composable
private fun HeroText(detail: SaleDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(detail.productLabel, style = NovoFonts.semibold(11).copy(color = NovoColors.textSecondary, letterSpacing = 0.5.sp))
        Text(detail.productTitle, style = NovoFonts.semibold(22))
        Text(
            Formatters.money(detail.amount) ?: "—",
            style = NovoFonts.heading(32).copy(color = NovoColors.accent),
            modifier = Modifier.padding(top = 4.dp),
        )
        detail.dateLabel?.let {
            Text(it, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TrackedLabel(title)
        content()
    }
}

@Composable
private fun MemberRow(member: SaleMemberRef) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(member.avatarColor),
        ) {
            Text(member.initials, style = NovoFonts.semibold(14))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, style = NovoFonts.semibold(15))
            member.memberIdLabel?.let {
                Text(it, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
        }
    }
}

/**
 * A plain sale gets a single icon row (no amount — the hero already shows
 * the total). A "pago combinado" gets one row per leg, each with its amount.
 */
@Composable
private fun PaymentCard(payments: List<SalePayment>) {
    if (payments.size == 1) {
        val payment = payments.first()
        IconRow(payment.icon, payment.label, payment.cardMask?.let { "Terminada en $it" })
    } else {
        Column(Modifier.fillMaxWidth().cardBackground()) {
            payments.forEachIndexed { index, payment ->
                PaymentLegRow(payment)
                if (index < payments.size - 1) RowDivider()
            }
        }
    }
}

@Composable
private fun PaymentLegRow(payment: SalePayment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconTile(payment.icon, 36, 10, 16)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(payment.label, style = NovoFonts.medium(14))
            if (!payment.cardMask.isNullOrEmpty()) {
                Text("Terminada en ${payment.cardMask}", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
        }
        payment.amount?.let {
            Text(Formatters.money(it) ?: "—", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, size: Int, corner: Int, iconSize: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        Icon(icon, contentDescription = null, tint = NovoColors.textPrimary, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun IconRow(icon: ImageVector, text: String, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        IconTile(icon, 40, 12, 18)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = NovoFonts.semibold(15))
            subtitle?.let { Text(it, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary)) }
        }
    }
}

@Composable
private fun LineItemsCard(items: List<SaleLineItem>) {
    Column(Modifier.fillMaxWidth().cardBackground()) {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SaleThumbnail(item.imageUrl, size = 36.dp, cornerRadius = 10.dp)
                Text(
                    if (item.quantity > 1) "${item.title} ×${item.quantity}" else item.title,
                    style = NovoFonts.medium(14),
                    modifier = Modifier.weight(1f),
                )
                item.price?.let {
                    Text(Formatters.money(it) ?: "—", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
                }
            }
            if (index < items.size - 1) RowDivider()
        }
    }
}
