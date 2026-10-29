package com.novofitclub.dashboard.feature.freezes

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.ThemedField
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.rememberPhotoCapture
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.SheetTopBar
import com.novofitclub.dashboard.feature.sales.TextAction
import com.novofitclub.dashboard.feature.sales.edit.SaleEditPayload
import com.novofitclub.dashboard.feature.sales.pickers.MemberPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.SaleMemberSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private class EvidenceItem(
    val id: String = UUID.randomUUID().toString(),
    val attachmentId: Int? = null,
    val localImage: Bitmap? = null,
    val thumbUrl: String? = null,
) {
    val isUploading: Boolean get() = attachmentId == null
}

/**
 * "Nueva solicitud de congelamiento" / editing a pending one — the web form
 * (congelados admin/views/freeze-form.php): member, type, days, evidence
 * photos and notes, against `POST/PUT congelados/v1/freezes`. Creating one
 * emails administración, who approves it. [member] presets the member
 * (Congelar from a member's screen). Host inside ScopedViewModels.
 */
@Composable
fun FreezeFormScreen(
    app: NovoFitClubApp,
    freeze: Freeze?,
    onDismiss: () -> Unit,
    onSaved: (Freeze) -> Unit,
    member: SaleEditPayload.MemberRef? = null,
) {
    val scope = rememberCoroutineScope()
    val memberSource: SaleMemberSource = viewModel { SaleMemberSource(app.api) }
    var selectedMember by remember {
        mutableStateOf(freeze?.memberId?.let { SaleEditPayload.MemberRef(it, freeze.memberName) } ?: member)
    }
    var type by remember { mutableStateOf(freeze?.type) }
    var daysText by remember { mutableStateOf(freeze?.days?.takeIf { it > 0 }?.toString().orEmpty()) }
    val evidence = remember {
        mutableStateListOf<EvidenceItem>().apply { freeze?.evidence?.forEach { add(EvidenceItem(attachmentId = it.id, thumbUrl = it.thumbUrl)) } }
    }
    var notes by remember { mutableStateOf(freeze?.notes.orEmpty()) }
    var showingMemberPicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val days = daysText.toIntOrNull() ?: 0
    val canSave = selectedMember != null && type != null && days >= 1 && evidence.none { it.isUploading } && !isSaving

    // Uploads right away, like the web form: the request only carries the attachment ids.
    val capture = rememberPhotoCapture(multiple = true) { photos ->
        photos.forEach { photo ->
            val item = EvidenceItem(localImage = photo.preview)
            evidence.add(item)
            scope.launch {
                try {
                    val response = app.api.uploadBytes("congelados/v1/evidence", photo.jpeg, "image/jpeg")
                    val id = response.firstNumber("id")?.toInt() ?: error("No se pudo subir la foto.")
                    val index = evidence.indexOfFirst { it.id == item.id }
                    if (index >= 0) evidence[index] = EvidenceItem(item.id, id, item.localImage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    evidence.removeAll { it.id == item.id }
                    errorMessage = e.localizedMessage ?: "No se pudo subir la foto."
                }
            }
        }
    }

    fun save() {
        val m = selectedMember ?: return
        val t = type ?: return
        scope.launch {
            isSaving = true
            errorMessage = null
            val body = JSONObject()
                .put("member_id", m.memberPostId)
                .put("freeze_type", t.raw)
                .put("days_requested", days)
                .put("evidence_ids", JSONArray(evidence.mapNotNull { it.attachmentId }))
                .put("notes", notes)
            try {
                val record = if (freeze != null) {
                    app.api.putObject("congelados/v1/freezes/${freeze.id}", body)
                } else {
                    app.api.postObject("congelados/v1/freezes", body)
                }
                Freeze.from(record)?.let(onSaved)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
            }
            isSaving = false
        }
    }

    BackHandler(enabled = !showingMemberPicker, onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(NovoColors.background)) {
        Column(Modifier.fillMaxSize()) {
            SheetTopBar(if (freeze == null) "Nueva solicitud" else "Editar solicitud", onCancel = onDismiss) {
                if (isSaving) {
                    CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.padding(horizontal = 16.dp).size(20.dp))
                } else {
                    TextAction(if (freeze == null) "Crear" else "Guardar", bold = true, enabled = canSave, onClick = ::save)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                // Miembro
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Miembro")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().cardBackground().clickable { showingMemberPicker = true }.padding(16.dp),
                    ) {
                        val m = selectedMember
                        if (m != null) {
                            Text(m.displayName, style = NovoFonts.semibold(15), modifier = Modifier.weight(1f))
                        } else {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(19.dp))
                                Text("Seleccionar miembro", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Tipo
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Tipo de congelamiento")
                    FreezeType.entries.forEach { option ->
                        val selected = type == option
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (selected) NovoColors.accent.copy(alpha = 0.12f) else NovoColors.card)
                                .border(1.5.dp, if (selected) NovoColors.accent else Color.Transparent, shape)
                                .clickable { type = option }
                                .padding(14.dp),
                        ) {
                            Icon(option.icon, contentDescription = null, tint = if (selected) NovoColors.accent else NovoColors.textPrimary, modifier = Modifier.width(32.dp).size(22.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(option.title, style = NovoFonts.semibold(15))
                                Text(option.detail, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                            }
                            Icon(
                                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (selected) NovoColors.accent else NovoColors.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                // Días
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Días solicitados")
                    ThemedField(daysText, { daysText = it.filter(Char::isDigit) }, placeholder = "Ej. 7", keyboardType = KeyboardType.Number)
                    Text(
                        "La evidencia adjunta (tiquetes, incapacidad) debe coincidir con estos días.",
                        style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                    )
                }

                // Evidencia
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Evidencia")
                    Text("Tiquetes, incapacidad u otra prueba.", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                    if (evidence.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            evidence.forEach { item ->
                                EvidenceThumb(item, onRemove = { evidence.removeAll { it.id == item.id } })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PhotoButton("Tomar foto", Icons.Outlined.PhotoCamera, Modifier.weight(1f), capture::takePhoto)
                        PhotoButton("Elegir fotos", Icons.Outlined.PhotoLibrary, Modifier.weight(1f), capture::pickFromGallery)
                    }
                }

                // Notas
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Notas")
                    TextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Cualquier detalle que el cliente indique…", style = NovoFonts.body(15).copy(color = NovoColors.textSecondary)) },
                        textStyle = NovoFonts.body(15),
                        minLines = 3,
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = NovoColors.card,
                            unfocusedContainerColor = NovoColors.card,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = NovoColors.accent,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    )
                }

                errorMessage?.let { Text(it, style = NovoFonts.medium(14).copy(color = NovoColors.accent)) }
            }
        }

        if (showingMemberPicker) {
            OverlayLayer {
                MemberPickerScreen(
                    source = memberSource,
                    alreadySelected = listOfNotNull(selectedMember),
                    singleSelection = true,
                    onDone = { picked ->
                        selectedMember = picked.firstOrNull() ?: selectedMember
                        showingMemberPicker = false
                    },
                    onCancel = { showingMemberPicker = false },
                )
            }
        }
    }
}

@Composable
private fun PhotoButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = CircleShape, border = BorderStroke(1.5.dp, NovoColors.accent), modifier = modifier) {
        Icon(icon, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(18.dp))
        Text("  $text", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
    }
}

@Composable
private fun EvidenceThumb(item: EvidenceItem, onRemove: () -> Unit) {
    Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(NovoColors.card)) {
        val local = item.localImage
        if (local != null) {
            Image(local.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(model = item.thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (item.isUploading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Quitar foto",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
            )
        }
    }
}
