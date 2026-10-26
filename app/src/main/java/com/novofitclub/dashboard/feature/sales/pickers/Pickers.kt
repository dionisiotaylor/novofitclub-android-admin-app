package com.novofitclub.dashboard.feature.sales.pickers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SaleThumbnail
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.CenteredSpinner
import com.novofitclub.dashboard.feature.sales.ReceptionistUser
import com.novofitclub.dashboard.feature.sales.SearchField
import com.novofitclub.dashboard.feature.sales.SheetTopBar
import com.novofitclub.dashboard.feature.sales.SkeletonBlock
import com.novofitclub.dashboard.feature.sales.TextAction
import com.novofitclub.dashboard.feature.sales.UnavailableState
import com.novofitclub.dashboard.feature.sales.edit.SaleEditPayload
import com.novofitclub.dashboard.feature.sales.pulsing
import kotlinx.coroutines.CancellationException

/** Pulsing placeholder row shown while the membership/product pickers load. */
@Composable
internal fun PickerRowSkeleton(showsThumbnail: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).pulsing(),
    ) {
        if (showsThumbnail) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(NovoColors.textSecondary.copy(alpha = 0.25f)),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(170.dp, 14.dp)
            SkeletonBlock(90.dp, 11.dp)
        }
    }
}

@Composable
private fun PickerScaffold(
    title: String,
    onCancel: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        SheetTopBar(title, onCancel, trailing)
        content()
    }
}

@Composable
private fun PickerRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                focusManager.clearFocus()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun SelectedCheck() {
    Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = NovoColors.accent, modifier = Modifier.size(22.dp))
}

// region Member picker

/**
 * Multi-select member picker for the sale editor. Selections are tracked
 * locally and only applied when the user taps "Listo", so cancelling makes
 * no change.
 */
@Composable
internal fun MemberPickerScreen(
    source: SaleMemberSource,
    alreadySelected: List<SaleEditPayload.MemberRef>,
    onDone: (List<SaleEditPayload.MemberRef>) -> Unit,
    onCancel: () -> Unit,
) {
    // Insertion-ordered so the sale keeps the order members were picked in.
    val selected = remember {
        mutableStateMapOf<Int, String>().apply { alreadySelected.forEach { put(it.memberPostId, it.displayName) } }
    }
    val order = remember { mutableStateListOf<Int>().apply { addAll(alreadySelected.map { it.memberPostId }) } }

    LaunchedEffect(Unit) {
        source.updateQuery("")
        source.load()
    }

    PickerScaffold(
        title = "Agregar miembros",
        onCancel = onCancel,
        trailing = {
            TextAction("Listo", bold = true, onClick = {
                onDone(order.mapNotNull { id -> selected[id]?.let { SaleEditPayload.MemberRef(id, it) } })
            })
        },
    ) {
        SearchField(
            value = source.query,
            onValueChange = source::updateQuery,
            placeholder = "Buscar por nombre",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        val error = source.errorMessage
        when {
            source.isLoading && source.members.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = NovoColors.accent) }
            error != null && source.members.isEmpty() -> UnavailableState(error)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(source.displayedMembers, key = { it.id }) { member ->
                    LaunchedEffect(member.id) { source.revealMoreIfNeeded(member) }
                    val postId = member.id.toIntOrNull()
                    PickerRow(onClick = {
                        if (postId == null) return@PickerRow
                        if (selected.containsKey(postId)) {
                            selected.remove(postId)
                            order.remove(postId)
                        } else {
                            selected[postId] = member.name
                            order.add(postId)
                        }
                    }) {
                        MemberAvatar(member.initials, member.avatarColor, member.photoUrl, 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(member.name, style = NovoFonts.medium(15))
                            Text("Cédula ${member.memberId}", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                        }
                        if (postId != null && selected.containsKey(postId)) SelectedCheck()
                    }
                }
                if (source.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Membership picker

/** Single-select membership picker — only offered when creating a new sale. */
@Composable
internal fun MembershipPickerScreen(
    catalog: ProductCatalogViewModel,
    onSelect: (ProductCatalogItem) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { catalog.load() }

    PickerScaffold(title = "Elegir membresía", onCancel = onCancel) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar membresía",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        val error = catalog.errorMessage
        when {
            catalog.isLoading -> Column { repeat(8) { PickerRowSkeleton(showsThumbnail = false) } }
            error != null && catalog.items.isEmpty() -> UnavailableState(error)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(catalog.memberships(query), key = { it.id }) { item ->
                    PickerRow(onClick = { onSelect(item) }) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = NovoFonts.medium(15))
                            Text(
                                Formatters.money(item.price) ?: "—",
                                style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Product picker

/** Multi-select physical-product picker for the sale editor. */
@Composable
internal fun ProductPickerScreen(
    catalog: ProductCatalogViewModel,
    alreadySelectedIds: Set<Int>,
    onDone: (List<ProductCatalogItem>) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Int>().apply { addAll(alreadySelectedIds) } }
    LaunchedEffect(Unit) { catalog.load() }

    PickerScaffold(
        title = "Agregar productos",
        onCancel = onCancel,
        trailing = {
            TextAction("Listo", bold = true, onClick = {
                onDone(catalog.items.filter { it.id in selectedIds && it.id !in alreadySelectedIds })
            })
        },
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar producto",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        val error = catalog.errorMessage
        when {
            catalog.isLoading -> Column { repeat(8) { PickerRowSkeleton() } }
            error != null && catalog.items.isEmpty() -> UnavailableState(error)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(catalog.physicalProducts(query), key = { it.id }) { item ->
                    val alreadyIn = item.id in alreadySelectedIds
                    PickerRow(
                        enabled = !alreadyIn,
                        modifier = Modifier.alpha(if (alreadyIn) 0.5f else 1f),
                        onClick = {
                            if (item.id in selectedIds) selectedIds.remove(item.id) else selectedIds.add(item.id)
                        },
                    ) {
                        SaleThumbnail(item.imageUrl, size = 36.dp, cornerRadius = 10.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = NovoFonts.medium(15))
                            Text(
                                Formatters.money(item.price)?.let { "$it · stock ${item.stock ?: 0}" } ?: "—",
                                style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                            )
                        }
                        when {
                            alreadyIn -> Text("Ya en la venta", style = NovoFonts.body(11).copy(color = NovoColors.textSecondary))
                            item.id in selectedIds -> SelectedCheck()
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Author picker

/**
 * Who-processed-this-sale picker, backed by `sale-creator/v1/authors`. That
 * endpoint doubles as the permission check: only an administrator gets a
 * 200 — everyone else sees the error state here.
 */
@Composable
internal fun AuthorPickerScreen(
    api: WPApiClient,
    onSelect: (Int, String) -> Unit,
    onCancel: () -> Unit,
) {
    var authors by remember { mutableStateOf<List<ReceptionistUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        isLoading = true
        errorMessage = null
        try {
            val raw = api.getArray("sale-creator/v1/authors")
            authors = raw.mapNotNull { fields ->
                val id = fields.firstString("id") ?: return@mapNotNull null
                ReceptionistUser(id, fields.firstString("name") ?: "Usuario #$id")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
        isLoading = false
    }

    PickerScaffold(title = "¿Quién hizo la venta?", onCancel = onCancel) {
        val error = errorMessage
        when {
            isLoading -> CenteredSpinner()
            error != null -> UnavailableState(error, onRetry = { attempt++ })
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(authors, key = { it.id }) { author ->
                    PickerRow(onClick = {
                        author.id.toIntOrNull()?.let { onSelect(it, author.name) }
                    }) {
                        Text(
                            author.name,
                            style = NovoFonts.medium(15),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// endregion
