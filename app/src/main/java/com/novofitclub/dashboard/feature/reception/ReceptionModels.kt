package com.novofitclub.dashboard.feature.reception

import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.util.Formatters
import org.json.JSONObject
import java.util.Date

/**
 * Per-method amounts as `novo-staff/v1` computes them — sales-agregator's
 * own helpers, so they match the "Resumen de ventas" / "Cierre de caja"
 * pages. Trade and pending-payment sales count as 0.
 */
data class SaleAmounts(val cash: Double = 0.0, val card: Double = 0.0, val sinpe: Double = 0.0, val total: Double = 0.0) {
    companion object {
        fun from(json: JSONObject?) = SaleAmounts(
            json?.firstNumber("cash") ?: 0.0,
            json?.firstNumber("card") ?: 0.0,
            json?.firstNumber("sinpe") ?: 0.0,
            json?.firstNumber("total") ?: 0.0,
        )
    }
}

/** One sale from `novo-staff/v1/sales` — always one the signed-in receptionist rang up (the server scopes it). */
data class ReceptionSale(
    val id: Int,
    val date: Date?,
    /** WP's native status: "pending" = held for admin review (e.g. the age rule) — listed, not counted in the day's totals. */
    val isPending: Boolean,
    val saleStatus: String,
    val buyers: List<String>,
    val items: List<String>,
    val paymentLabel: String,
    val amounts: SaleAmounts,
    val notes: List<String>,
    /** Has a "Diferencia / Ajuste" line — shown purple, as in wp-admin. */
    val hasAjuste: Boolean,
) {
    /** Paid by canje (status "trade"): nothing charged, shown blue. */
    val isCanje: Boolean get() = saleStatus == "trade"

    val statusLabel: String?
        get() = when {
            isPending -> "En revisión"
            saleStatus == "pending_payment" -> "Pago pendiente"
            saleStatus == "refunded" -> "Reembolsada"
            saleStatus == "cancelled" -> "Cancelada"
            else -> null
        }

    companion object {
        fun from(json: JSONObject): ReceptionSale? {
            val id = json.firstNumber("id")?.toInt() ?: return null
            val items = mutableListOf<String>()
            json.obj("membership")?.let { items.add(it.firstString("name", "title") ?: "Membresía") }
            for (key in listOf("physical_products", "services")) {
                json.arr(key)?.objects().orEmpty().forEach { product ->
                    val name = product.firstString("name", "title") ?: "Producto"
                    val quantity = (product.firstNumber("quantity") ?: 1.0).toInt()
                    items.add(if (quantity > 1) "$name ×$quantity" else name)
                }
            }
            val ajustes = json.arr("ajustes")?.objects().orEmpty()
            ajustes.forEach { ajuste ->
                val days = (ajuste.firstNumber("days") ?: 0.0).toInt()
                items.add("Diferencia: ${ajuste.firstString("concept").orEmpty()} (${Formatters.colones(ajuste.firstNumber("amount") ?: 0.0)}${if (days > 0) ", +$days d" else ""})")
            }
            val payment = if (json.str("payment") == "multiple_payment") {
                json.arr("multiple_payment")?.objects().orEmpty().mapNotNull { it.firstString("payment") }.joinToString(" + ") { methodLabel(it) }
            } else {
                json.firstString("single_payment")?.let { methodLabel(it) }.orEmpty()
            }
            return ReceptionSale(
                id = id,
                date = Formatters.parseDate(json.firstString("date")),
                isPending = json.str("post_status") == "pending",
                saleStatus = json.str("sale_status").orEmpty(),
                buyers = json.arr("members")?.objects().orEmpty().mapNotNull { it.firstString("name") },
                items = items,
                paymentLabel = payment,
                amounts = SaleAmounts.from(json.obj("amounts")),
                notes = json.arr("notes")?.objects().orEmpty().mapNotNull { it.firstString("note") },
                hasAjuste = ajustes.isNotEmpty(),
            )
        }

        fun methodLabel(raw: String) = when (raw) {
            "cash" -> "Efectivo"
            "card" -> "Tarjeta"
            "sinpe" -> "SINPE"
            else -> raw.replaceFirstChar { it.uppercase() }
        }
    }
}

/** A cierre de caja from `novo-staff/v1/closings` (own closings only, server-side). */
data class ReceptionClosing(
    val id: Int,
    val date: Date?,
    val bills: Double,
    val coins: Double,
    val totalCash: Double,
    val totalCard: Double,
    val totalSinpe: Double,
    val total: Double,
    val sobreAdded: Double,
    val notes: String,
) {
    companion object {
        fun from(json: JSONObject): ReceptionClosing? {
            val id = json.firstNumber("id")?.toInt() ?: return null
            fun n(key: String) = json.firstNumber(key) ?: 0.0
            return ReceptionClosing(
                id, Formatters.parseDate(json.firstString("date")),
                n("bills"), n("coins"), n("total_cash"), n("total_card"), n("total_sinpe"), n("total"), n("sobre_added"),
                json.firstString("notes").orEmpty(),
            )
        }
    }
}
