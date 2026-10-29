package com.novofitclub.dashboard.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.lock.AppLockManager
import com.novofitclub.dashboard.core.net.AuthManager
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.feature.coach.hexColor

@Composable
fun SettingsScreen(auth: AuthManager, appLock: AppLockManager, api: WPApiClient, onLogout: () -> Unit) {
    val state by auth.state.collectAsState()
    val lockEnabled by appLock.isEnabled.collectAsState()
    val viewing by auth.viewingCoach.collectAsState()
    var choosingCoach by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = "Ajustes")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().cardBackground()) {
                InfoRow("Sitio", state.siteUrl)
                HorizontalDivider(color = NovoColors.separator)
                InfoRow("Usuario", state.username)
                state.role?.let {
                    HorizontalDivider(color = NovoColors.separator)
                    InfoRow("Rol", ROLE_LABELS[it] ?: "Sin rol")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Bloquear con huella", style = NovoFonts.medium(14))
                    Text(
                        "Pide confirmar tu identidad cada vez que abres la app",
                        style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                    )
                }
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = appLock::setEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = NovoColors.accent),
                )
            }

            if (state.role == "admin") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardBackground()
                        .clickable { if (viewing != null) auth.viewingCoach.value = null else choosingCoach = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = NovoColors.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(viewing?.let { "Dejar de ver como ${it.firstName}" } ?: "Ver como entrenador", style = NovoFonts.medium(14))
                        Text(
                            "La app de un coach con sus datos reales: horario, citas y programas. Lo que cambies ahí se guarda de verdad.",
                            style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                        )
                    }
                }
            }

            SecondaryButton("Cerrar sesión", onClick = onLogout)
        }
    }

    if (choosingCoach) {
        CoachPickerDialog(api, onDismiss = { choosingCoach = false }) { auth.viewingCoach.value = it }
    }
}

private val ROLE_LABELS = mapOf("admin" to "Administrador", "receptionist" to "Recepción", "coach" to "Entrenador")

/** The coaches an admin can look at the app as (`novo-staff/v1/coaches`). */
@Composable
private fun CoachPickerDialog(api: WPApiClient, onDismiss: () -> Unit, onPick: (ViewedCoach) -> Unit) {
    var coaches by remember { mutableStateOf<List<ViewedCoach>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            coaches = api.getArray("novo-staff/v1/coaches").mapNotNull { json ->
                json.num("id")?.toInt()?.let { ViewedCoach(it, json.str("name").orEmpty(), json.str("color")) }
            }
        } catch (e: Exception) {
            error = e.localizedMessage
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ver como entrenador") },
        text = {
            val list = coaches
            when {
                error != null -> Text(error.orEmpty())
                list == null -> CircularProgressIndicator(color = NovoColors.accent)
                else -> Column {
                    list.forEach { coach ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onPick(coach)
                                onDismiss()
                            }.padding(vertical = 10.dp),
                        ) {
                            MemberAvatar(initials(coach.name), hexColor(coach.color) ?: NovoColors.avatarPalette[0], null, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(coach.name, style = NovoFonts.semibold(15))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
        Spacer(Modifier.width(16.dp))
        Text(value, style = NovoFonts.semibold(15), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
