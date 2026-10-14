package com.novofitclub.dashboard.feature.cash.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.net.displayString
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import org.json.JSONObject

/**
 * Generic "raw fields" detail screen. Since the exact JSON shape returned by
 * each custom plugin endpoint isn't documented, this renders every key/value
 * pair it receives, sorted by key.
 */
@Composable
fun RecordDetailScreen(
    title: String,
    record: JSONObject,
    onBack: () -> Unit,
    breadcrumb: String? = null,
) {
    val sortedKeys = record.keys().asSequence().toList().sorted()
    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        SectionHeader(title = title, breadcrumb = breadcrumb, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (sortedKeys.isEmpty()) return@Column
            SelectionContainer {
                Column(Modifier.fillMaxWidth().cardBackground()) {
                    sortedKeys.forEachIndexed { index, key ->
                        val value = record.opt(key).displayString()
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                key,
                                style = NovoFonts.medium(13).copy(color = NovoColors.textSecondary),
                                modifier = Modifier.width(130.dp),
                            )
                            Text(
                                value.ifEmpty { "—" },
                                style = NovoFonts.body(15),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (index < sortedKeys.size - 1) CardDivider()
                    }
                }
            }
        }
    }
}
