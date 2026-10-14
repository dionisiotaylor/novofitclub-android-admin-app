package com.novofitclub.dashboard.feature.cash.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton

/**
 * Android counterpart of SwiftUI's `ContentUnavailableView`: big gray icon,
 * bold title and an optional description, centered in the remaining space.
 * Scrollable so it also works as pull-to-refresh content.
 */
@Composable
fun ContentUnavailable(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        ) {
            Icon(icon, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(44.dp))
            Text(title, style = NovoFonts.title(20), textAlign = TextAlign.Center)
            if (!description.isNullOrEmpty()) {
                Text(
                    description,
                    style = NovoFonts.body(14).copy(color = NovoColors.textSecondary),
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                SecondaryButton("Reintentar", onRetry, Modifier.padding(top = 8.dp).width(200.dp))
            }
        }
    }
}

/** "No se pudo cargar" + error text — shared error state for every Caja screen. */
@Composable
fun LoadFailed(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ContentUnavailable(
        title = "No se pudo cargar",
        icon = Icons.Outlined.Warning,
        description = message,
        onRetry = onRetry,
        modifier = modifier,
    )
}

/** Thin separator between rows inside a single card. */
@Composable
fun CardDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = NovoColors.separator)
}

/** "1 movimiento" / "3 movimientos". */
fun pluralCount(count: Int, singular: String): String = "$count $singular${if (count == 1) "" else "s"}"
