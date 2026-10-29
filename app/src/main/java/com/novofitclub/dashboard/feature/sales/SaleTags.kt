package com.novofitclub.dashboard.feature.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.tintedCard
import org.json.JSONObject
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString

/** The tags a sale can carry. A new kind of sale gets an entry here and shows up as a pill above the amount in every sales list. */
enum class SaleTag(val label: String, val color: Color) {
    /** Paid by canje: nothing was charged. */
    Canje("Canje", Color(0xFF2563EB)),

    /** Has a "Diferencia / Ajuste" line, as in the wp-admin Sales list. */
    Ajuste("Diferencia", Color(0xFF7C3AED));

    companion object {
        fun of(isCanje: Boolean, hasAjuste: Boolean): List<SaleTag> = buildList {
            if (isCanje) add(Canje)
            if (hasAjuste) add(Ajuste)
        }
    }
}

@Composable
fun SaleTagPill(tag: SaleTag) {
    Text(
        tag.label,
        style = NovoFonts.semibold(11).copy(color = Color.White),
        modifier = Modifier.clip(CircleShape).background(tag.color).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A sale's tags side by side; in a list row they sit right-aligned above the amount. Draws nothing when there are none. */
@Composable
fun SaleTagsView(tags: List<SaleTag>) {
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { tags.forEach { SaleTagPill(it) } }
}

/** Blue card for a sale paid by canje, purple for one with a "Diferencia / Ajuste", plain card otherwise. */
fun Modifier.saleRowBackground(isCanje: Boolean, hasAjuste: Boolean): Modifier = when {
    isCanje -> tintedCard(NovoColors.canje)
    hasAjuste -> tintedCard(NovoColors.ajuste)
    else -> cardBackground()
}

/** The amount's colour in a list row: blue for canje, purple for a Diferencia, else the accent. */
fun saleAmountColor(isCanje: Boolean, hasAjuste: Boolean): Color = when {
    isCanje -> NovoColors.canje
    hasAjuste -> NovoColors.ajuste
    else -> NovoColors.accent
}

/** One "Diferencia / Ajuste" line of a sale: what it charges for, its amount and the days it adds. */
data class SaleAjuste(val concept: String, val amount: Double, val days: Int) {
    companion object {
        fun from(json: JSONObject) = SaleAjuste(
            json.firstString("concept").orEmpty(),
            json.firstNumber("amount") ?: 0.0,
            (json.firstNumber("days") ?: 0.0).toInt(),
        )
    }
}
