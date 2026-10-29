package com.novofitclub.dashboard.feature.members

import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.imageUrl
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.util.Formatters
import org.json.JSONArray
import org.json.JSONObject

/**
 * The subset of the iOS `SaleDetail` model the member's sales list needs,
 * parsed from the same `sale-creator/v1/sales/{id}` item shape that
 * `sale-creator/v1/members/{id}/sales` returns. Kept local to this package
 * (the full model belongs to the Sales feature) with identical rules:
 * title = membership → first physical product → first service → "Venta";
 * amount = membership price + Σ(product price × qty) + Σ service price;
 * image = first physical product's photo, else the membership's.
 */
data class MemberSale(
    val id: String,
    val productTitle: String,
    val amount: Double,
    val imageUrl: String?,
    val dateLabel: String?,
    val hasAjuste: Boolean = false,
    /** Paid by canje: ₡0 and blue, as in the sales list. */
    val isCanje: Boolean = false,
) {
    private data class LineItem(val title: String, val price: Double?, val quantity: Int, val imageUrl: String?)

    companion object {
        fun from(id: String, fields: JSONObject): MemberSale {
            val membership = fields.obj("membership")
            val membershipTitle = membership?.firstString("title", "name")
            val membershipPrice = membership?.firstNumber("price") ?: 0.0
            val products = lineItems(fields.arr("physical_products"))
            val services = lineItems(fields.arr("services"))

            val productsTotal = products.sumOf { (it.price ?: 0.0) * it.quantity }
            val servicesTotal = services.sumOf { it.price ?: 0.0 }

            val ajustes = fields.arr("ajustes")?.objects().orEmpty()
            val ajustesTotal = ajustes.sumOf { it.firstNumber("amount") ?: 0.0 }
            val isCanje = fields.firstString("status") == "trade"

            val title = membershipTitle
                ?: products.firstOrNull()?.title
                ?: services.firstOrNull()?.title
                ?: ajustes.firstOrNull()?.firstString("concept")?.let { "Diferencia: $it" }
                ?: "Venta"

            return MemberSale(
                id = id,
                productTitle = title,
                amount = if (isCanje) 0.0 else membershipPrice + productsTotal + servicesTotal + ajustesTotal,
                hasAjuste = ajustes.isNotEmpty(),
                isCanje = isCanje,
                imageUrl = products.firstOrNull()?.imageUrl ?: membership?.imageUrl(),
                dateLabel = Formatters.friendlyDate(fields.firstString("post_date", "date_created")),
            )
        }

        private fun lineItems(raw: JSONArray?): List<LineItem> {
            if (raw == null) return emptyList()
            return (0 until raw.length()).mapNotNull { i ->
                val o = raw.optJSONObject(i) ?: return@mapNotNull null
                val title = o.firstString("title", "name") ?: return@mapNotNull null
                LineItem(
                    title = title,
                    price = o.firstNumber("price"),
                    quantity = maxOf((o.firstNumber("quantity") ?: 1.0).toInt(), 1),
                    imageUrl = o.imageUrl(),
                )
            }
        }
    }
}
