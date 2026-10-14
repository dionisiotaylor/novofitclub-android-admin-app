package com.novofitclub.dashboard.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.BrandBadge
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton

@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize().background(NovoColors.background).safeDrawingPadding(),
    ) {
        Spacer(Modifier.weight(1f))
        BrandBadge(width = 110.dp)
        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(48.dp))
        Text("Novo Fit Club está bloqueado", style = NovoFonts.semibold(17))
        if (error != null) {
            Text(
                error,
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Desbloquear", onUnlock, Modifier.padding(start = 40.dp, end = 40.dp, bottom = 40.dp))
    }
}
