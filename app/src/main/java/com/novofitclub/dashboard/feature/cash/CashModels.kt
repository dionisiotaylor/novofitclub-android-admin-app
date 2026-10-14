package com.novofitclub.dashboard.feature.cash

import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.util.Formatters
import org.json.JSONObject
import java.util.Date

/**
 * A JSON object wrapped with a stable identity so it can drive a list
 * regardless of which field the backend actually uses as a primary key
 * (port of the iOS `IdentifiableRecord`).
 */
class IdentifiableRecord(val fields: JSONObject, fallbackIndex: Int) {
    val id: String = fields.str("id") ?: "row-$fallbackIndex"

    companion object {
        fun from(records: List<JSONObject>): List<IdentifiableRecord> =
            records.mapIndexed { index, record -> IdentifiableRecord(record, index) }
    }
}

/**
 * One `wp/v2/register_balance` post — a daily register close. `acf` carries
 * the real breakdown (`total_cash`, `total_sinpe`, `total_card`, `total`).
 * Every ACF field is defined as free text, not number: values arrive as
 * strings ("500.00"), sometimes with thousands separators ("116,100.00"), and
 * an unfilled field is `""` rather than "0" or null. `bills`/`coins` are
 * deliberately not parsed here — they aren't sale amounts.
 */
class RegisterBalance private constructor(
    val id: Int,
    val receptionistName: String,
    val date: Date?,
    val cashAmount: Double,
    val sinpeAmount: Double,
    val cardAmount: Double,
    val totalAmount: Double,
) {
    companion object {
        fun from(fields: JSONObject): RegisterBalance? {
            val id = fields.firstNumber("id") ?: return null
            val rawTitle = fields.obj("title")?.firstString("rendered") ?: ""
            val acf = fields.obj("acf") ?: JSONObject()
            return RegisterBalance(
                id = id.toInt(),
                receptionistName = extractName(rawTitle) ?: rawTitle,
                date = Formatters.parseDate(fields.firstString("date")),
                cashAmount = parseAmount(acf.str("total_cash")),
                sinpeAmount = parseAmount(acf.str("total_sinpe")),
                cardAmount = parseAmount(acf.str("total_card")),
                totalAmount = parseAmount(acf.str("total")),
            )
        }

        /** Strips thousands separators; empty/unparsable text counts as zero. */
        private fun parseAmount(raw: String?): Double {
            if (raw.isNullOrEmpty()) return 0.0
            return raw.replace(",", "").trim().toDoubleOrNull() ?: 0.0
        }

        /** "Cierre de Ana Pérez del día 16/09/2026" -> "Ana Pérez". */
        private fun extractName(title: String): String? {
            val marker = "Cierre de "
            val start = title.indexOf(marker)
            if (start < 0) return null
            val nameStart = start + marker.length
            val end = title.indexOf(" del día", nameStart)
            if (end < 0) return null
            return title.substring(nameStart, end).ifEmpty { null }
        }
    }
}

/**
 * One entry from `sobre-tracking/v1/movements` — each receptionist
 * accumulates cash from their register closes into a virtual "envelope"
 * (`deposito`, usually automatic, linked to the `register_balance` post via
 * `source_post_id`), and an admin periodically withdraws it (`retiro`).
 */
class SobreMovement private constructor(
    val id: Int,
    val kind: Kind,
    val amount: Double,
    val notes: String?,
    val receptionistName: String,
    val receptionistAvatarUrl: String?,
    val isAuto: Boolean,
    val sourcePostId: Int?,
    val createdAt: Date?,
) {
    enum class Kind(val raw: String) { Deposito("deposito"), Retiro("retiro") }

    companion object {
        fun from(fields: JSONObject): SobreMovement? {
            val id = fields.firstNumber("id") ?: return null
            val kindRaw = fields.firstString("type") ?: return null
            val kind = Kind.entries.firstOrNull { it.raw == kindRaw } ?: return null
            return SobreMovement(
                id = id.toInt(),
                kind = kind,
                amount = fields.firstNumber("amount") ?: 0.0,
                notes = fields.firstString("notes"),
                receptionistName = fields.firstString("receptionist_name") ?: "—",
                receptionistAvatarUrl = fields.firstString("receptionist_avatar"),
                isAuto = fields.bool("is_auto") ?: false,
                sourcePostId = fields.firstNumber("source_post_id")?.toInt(),
                createdAt = Formatters.parseDate(fields.firstString("created_at")),
            )
        }
    }
}

/** One row from `sobre-tracking/v1/balances` — a receptionist's unwithdrawn cash total. */
class SobreBalance private constructor(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    val balance: Double,
) {
    companion object {
        fun from(fields: JSONObject): SobreBalance? {
            val id = fields.firstNumber("receptionist_id") ?: return null
            return SobreBalance(
                id = id.toInt(),
                name = fields.firstString("receptionist_name") ?: "—",
                avatarUrl = fields.firstString("avatar"),
                balance = fields.firstNumber("balance") ?: 0.0,
            )
        }
    }
}
