package com.novofitclub.dashboard.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.net.AuthManager
import com.novofitclub.dashboard.core.ui.BrandBadge
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.ThemedField
import com.novofitclub.dashboard.core.ui.cardBackground
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(auth: AuthManager) {
    val state by auth.state.collectAsState()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canSubmit = username.isNotBlank() && password.isNotEmpty()

    Column(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(NovoColors.background)
            .clickable(indication = null, interactionSource = null) { focus.clearFocus() }
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            BrandBadge(width = 110.dp)
            Text("Novo Staff", style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
        ) {
            SectionLabel("Tu usuario de WordPress")
            ThemedField(username, { username = it }, "Usuario o correo")
            ThemedField(password, { password = it }, "Contraseña", isSecure = true)
        }

        state.loginError?.let {
            Text(it, style = NovoFonts.medium(14).copy(color = NovoColors.accent), modifier = Modifier.fillMaxWidth())
        }

        PrimaryButton(
            text = "Iniciar sesión",
            enabled = canSubmit,
            loading = state.isValidating,
            onClick = {
                focus.clearFocus()
                scope.launch { auth.login(username, password) }
            },
        )

        Text(
            "Es el mismo usuario y contraseña con el que entras a novofitclub.com/wp-admin.",
            style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}
