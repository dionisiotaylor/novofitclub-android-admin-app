package com.novofitclub.dashboard.feature.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.lock.AppLockManager
import com.novofitclub.dashboard.core.net.AuthManager
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground

@Composable
fun SettingsScreen(auth: AuthManager, appLock: AppLockManager) {
    val state by auth.state.collectAsState()
    val lockEnabled by appLock.isEnabled.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = "Ajustes")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().cardBackground()) {
                InfoRow("Sitio", state.siteUrl)
                HorizontalDivider(color = NovoColors.separator)
                InfoRow("Usuario", state.username)
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

            SecondaryButton("Cerrar sesión", onClick = auth::logout)
        }
    }
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
