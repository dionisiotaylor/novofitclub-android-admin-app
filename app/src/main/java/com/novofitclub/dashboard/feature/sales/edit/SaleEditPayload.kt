package com.novofitclub.dashboard.feature.sales.edit

import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.imageUrl
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.feature.sales.pickers.AgeRestriction
import com.novofitclub.dashboard.feature.sales.pickers.ProductCatalogItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * The editable draft of a sale, parsed from `GET sale-creator/v1/sales/{id}`
 * and serialized back into the very different shape `PUT` expects
 * (confirmed against the plugin's `class-sale-repository.php` /
 * `class-validation.php`):
 * - `membership`/`physical_products`/`services`/`members` come back on GET
 *   as full objects but go out as bare ids (`membership_id`,
 *   `{product_id, quantity}`, `member_ids`).
 * - The PUT is a full replace, not a patch — omitting a field (e.g. `notes`)
 *   deletes it server-side, so [patchBody] always emits every field.
 * - Changing `membership_id` on an existing sale double-extends the member's
 *   expiration date (a real backend bug, not yet fixed), so the edit screen
 *   only offers the membership picker when creating; on edit it round-trips
 *   unchanged.
 */
data class SaleEditPayload(
    val status: SaleStatus = SaleStatus.Completed,
    val dateCreated: String = today(),
    /** Loaded from an existing sale (edit mode) rather than started blank: drives the locks below. */
    val isSaved: Boolean = false,
    val membershipId: Int? = null,
    val membershipLabel: String? = null,
    val membershipPrice: Double = 0.0,
    val membershipAgeRestriction: AgeRestriction? = null,
    /** How many members the picked membership is for ("2 personas"). */
    val membershipPeople: Int = 1,
    val physicalProducts: List<ProductLine> = emptyList(),
    val services: List<ProductLine> = emptyList(),
    val members: List<MemberRef> = emptyList(),
    val ajustes: List<AjusteLine> = emptyList(),
    val notes: List<NoteEntry> = emptyList(),
    /**
     * Who processed the sale (`post_author`). Only takes effect for an
     * existing sale and only when the authenticated user is an administrator
     * (a non-admin sending it is silently ignored server-side).
     */
    val authorId: Int? = null,
    val authorName: String? = null,
    val isMultiplePayment: Boolean = false,
    val singlePayment: PaymentMethod = PaymentMethod.Cash,
    val singleCardNumber: String = "",
    val paymentLegs: List<PaymentLeg> = emptyList(),
) {
    data class ProductLine(
        val productId: Int,
        val quantity: Int,
        val title: String,
        val price: Double?,
        val imageUrl: String?,
        /**
         * Units left, for a product just picked from the catalog. Null on a
         * saved sale's lines — the server re-checks stock, counting the units
         * that sale already took (same as the web form).
         */
        val stock: Int? = null,
    ) {
        val lineTotal: Double get() = (price ?: 0.0) * quantity
        val maxQuantity: Int get() = stock?.let { maxOf(it, 1) } ?: 99
    }

    /** [age] comes from the member picker, for the Estudiante / Ciudadano de Oro rule. */
    data class MemberRef(val memberPostId: Int, val displayName: String, val age: Int? = null)

    /**
     * A "Diferencia / Ajuste" line (themes/.../app/sales/ajustes.php): a
     * free-text concept and amount for a price difference, without a one-off
     * product. Optional days extend the members' access like a day pass.
     * Every sale with one emails administración and shows purple in the lists.
     */
    data class AjusteLine(
        val id: String = UUID.randomUUID().toString(),
        val concept: String = "",
        val amountText: String = "",
        val daysText: String = "",
    ) {
        val amount: Double get() = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
        val days: Int get() = daysText.toIntOrNull() ?: 0
        val trimmedConcept: String get() = concept.trim()
    }

    enum class PaymentMethod(val raw: String, val label: String) {
        Cash("cash", "Efectivo"),
        Card("card", "Tarjeta"),
        Sinpe("sinpe", "SINPE Móvil");

        companion object {
            fun from(raw: String?): PaymentMethod? = entries.firstOrNull { it.raw == raw?.lowercase() }
        }
    }

    data class PaymentLeg(
        val id: String = UUID.randomUUID().toString(),
        val method: PaymentMethod = PaymentMethod.Cash,
        val amountText: String = "",
        val cardNumber: String = "",
    ) {
        val amount: Double get() = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    enum class SaleStatus(val raw: String, val label: String) {
        Completed("completed", "Completada"),
        PendingPayment("pending_payment", "Pago pendiente"),
        Refunded("refunded", "Reembolsada"),
        Cancelled("cancelled", "Cancelada"),
        OnHold("on_hold", "En espera"),
        Failed("failed", "Fallida"),
        Trade("trade", "Canje");

        companion object {
            fun from(raw: String?): SaleStatus? = entries.firstOrNull { it.raw == raw }
        }
    }

    data class NoteEntry(
        val id: String = UUID.randomUUID().toString(),
        val date: String,
        val text: String,
    )

    // region Mutations

    fun withMembership(item: ProductCatalogItem) = copy(
        membershipId = item.id,
        membershipLabel = item.title,
        membershipPrice = item.price,
        membershipAgeRestriction = item.ageRestriction,
        membershipPeople = item.people,
    )

    fun clearingMembership() = copy(
        membershipId = null,
        membershipLabel = null,
        membershipPrice = 0.0,
        membershipAgeRestriction = null,
        membershipPeople = 1,
    )

    fun addingAjuste() = copy(ajustes = ajustes + AjusteLine())

    fun addingNote() = copy(notes = notes + NoteEntry(date = today(), text = ""))

    fun addingPaymentLeg() = copy(paymentLegs = paymentLegs + PaymentLeg())

    // endregion

    // region Validation

    val computedTotal: Double
        get() = membershipPrice + physicalProducts.sumOf { it.lineTotal } + services.sumOf { it.lineTotal } + ajusteTotal

    val ajusteTotal: Double get() = ajustes.sumOf { it.amount }
    val ajusteDays: Int get() = ajustes.sumOf { it.days }

    // Locks on a saved sale (mirror the web form, sale-creator.js renderEditLocks()):
    // its membership, members and ajustes decided whose dates it renewed.
    val membershipLocked: Boolean get() = isSaved
    val membersLocked: Boolean get() = isSaved && (membershipId != null || ajusteDays > 0)
    val ajustesLocked: Boolean get() = isSaved

    /**
     * Members the picked membership's age rule leaves out. A warning, not a
     * block: the sale still goes through, held "Pendiente de revisión" for
     * administración (themes/.../app/sales/age-restriction.php).
     */
    val ageViolators: List<MemberRef>
        get() {
            val rule = membershipAgeRestriction ?: return emptyList()
            return members.filter { member -> member.age?.let { !rule.allows(it) } ?: false }
        }

    /** A membership for N people needs exactly N members — new sales only, same wording as the server (SlcrValidation::people_count_error()). */
    val peopleProblem: String?
        get() {
            if (isSaved || membershipId == null) return null
            val count = members.size
            if (count == membershipPeople || (count == 0 && membershipPeople == 1)) return null
            val diff = abs(membershipPeople - count)
            val what = if (count < membershipPeople) {
                "agrega $diff ${if (diff == 1) "miembro más" else "miembros más"}"
            } else {
                "quita $diff ${if (diff == 1) "miembro" else "miembros"}"
            }
            return "Este plan es para $membershipPeople ${if (membershipPeople == 1) "persona" else "personas"}: $what."
        }

    /** Why Guardar is off, for the rules a person can't see from the form itself — mirrors `SlcrValidation::validate_ajustes()`. */
    val ajusteProblem: String?
        get() {
            for (line in ajustes) {
                if (line.trimmedConcept.isEmpty()) return "Cada diferencia necesita un concepto."
                if (line.trimmedConcept.length > 120) return "El concepto de la diferencia es muy largo (máximo 120 caracteres)."
                if (line.amount <= 0) return "Cada diferencia necesita un monto mayor a cero."
                if (line.days < 0 || line.days > 365) return "Los días que suma una diferencia van de 0 a 365."
            }
            if (ajusteDays > 0 && members.isEmpty()) return "Una diferencia que suma días necesita un miembro."
            if (ajusteDays > 0 && membershipId != null) {
                return "Una diferencia que suma días no puede ir junto con una membresía. Regístralas por separado."
            }
            return null
        }

    val paymentLegsTotal: Double get() = paymentLegs.sumOf { it.amount }

    /**
     * Mirrors the backend's own checks so a mismatch surfaces before tapping
     * Guardar instead of as a 400 afterward.
     */
    val isPaymentValid: Boolean
        get() = if (isMultiplePayment) {
            paymentLegs.isNotEmpty() &&
                paymentLegs.none { it.method == PaymentMethod.Card && it.cardNumber.isBlank() } &&
                abs(paymentLegsTotal - computedTotal) < 1
        } else {
            !(singlePayment == PaymentMethod.Card && singleCardNumber.isBlank())
        }

    val canSave: Boolean
        get() {
            if (!isPaymentValid) return false
            if (membershipId == null && physicalProducts.isEmpty() && services.isEmpty() && ajustes.isEmpty()) return false
            if (membershipId != null && members.isEmpty()) return false
            if (peopleProblem != null) return false
            if (!ajustesLocked && ajusteProblem != null) return false
            return true
        }

    // endregion

    /** Body for `PUT sale-creator/v1/sales/{id}` (full replace) and `POST sale-creator/v1/sales`. */
    fun patchBody(): JSONObject {
        val body = JSONObject()
        body.put("status", status.raw)
        body.put("date_created", dateCreated)
        body.put("physical_products", JSONArray(physicalProducts.map { productJson(it) }))
        body.put("services", JSONArray(services.map { productJson(it) }))
        body.put("member_ids", JSONArray(members.map { it.memberPostId }))
        body.put(
            "ajustes",
            JSONArray(ajustes.map { JSONObject().put("concept", it.trimmedConcept).put("amount", it.amount).put("days", it.days) }),
        )
        body.put("notes", JSONArray(notes.map { JSONObject().put("date", it.date).put("note", it.text) }))
        body.put("membership_id", membershipId ?: JSONObject.NULL)
        authorId?.let { body.put("post_author", it) }
        if (isMultiplePayment) {
            body.put("payment", "multiple_payment")
            body.put(
                "multiple_payment",
                JSONArray(
                    paymentLegs.map {
                        JSONObject()
                            .put("payment", it.method.raw)
                            .put("amount", it.amount)
                            .put("card_number", it.cardNumber)
                    },
                ),
            )
        } else {
            body.put("payment", "single_payment")
            body.put("single_payment", singlePayment.raw)
            body.put("card_number", singleCardNumber)
        }
        return body
    }

    companion object {
        private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        private fun productJson(line: ProductLine) =
            JSONObject().put("product_id", line.productId).put("quantity", line.quantity)

        /** Parses `GET sale-creator/v1/sales/{id}`. */
        fun fromFields(fields: JSONObject): SaleEditPayload {
            val membership = fields.obj("membership")
            val author = fields.obj("author")

            val membersArray = fields.arr("members")
            val members = (0 until (membersArray?.length() ?: 0)).mapNotNull { index ->
                val obj = membersArray?.optJSONObject(index) ?: return@mapNotNull null
                val memberId = obj.firstNumber("id") ?: return@mapNotNull null
                val raw = obj.firstString("name") ?: ""
                MemberRef(memberId.toInt(), splitEmbeddedId(raw).first ?: raw)
            }

            val notesArray = fields.arr("notes")
            val notes = (0 until (notesArray?.length() ?: 0)).mapNotNull { index ->
                val obj = notesArray?.optJSONObject(index) ?: return@mapNotNull null
                val text = obj.firstString("note", "text", "content", "body") ?: ""
                if (text.isEmpty()) return@mapNotNull null
                NoteEntry(date = obj.firstString("date") ?: today(), text = text)
            }

            val legsArray = fields.arr("multiple_payment")
            val isMultiple = legsArray != null && legsArray.length() > 0
            val legs = if (!isMultiple) emptyList() else (0 until legsArray!!.length()).mapNotNull { index ->
                val obj = legsArray.optJSONObject(index) ?: return@mapNotNull null
                val method = PaymentMethod.from(obj.firstString("payment")) ?: return@mapNotNull null
                val amount = obj.firstNumber("amount") ?: 0.0
                PaymentLeg(
                    method = method,
                    amountText = if (amount == 0.0) "" else String.format(Locale.US, "%.0f", amount),
                    cardNumber = obj.firstString("card_number") ?: "",
                )
            }

            val ajustes = fields.arr("ajustes")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().map { obj ->
                val amount = obj.firstNumber("amount") ?: 0.0
                val days = (obj.firstNumber("days") ?: 0.0).toInt()
                AjusteLine(
                    concept = obj.firstString("concept").orEmpty(),
                    amountText = if (amount == 0.0) "" else String.format(Locale.US, "%.0f", amount),
                    daysText = if (days == 0) "" else days.toString(),
                )
            }

            return SaleEditPayload(
                status = SaleStatus.from(fields.firstString("status")) ?: SaleStatus.Completed,
                dateCreated = fields.firstString("date_created") ?: today(),
                isSaved = true,
                ajustes = ajustes,
                membershipId = membership?.let { (it.firstNumber("id") ?: 0.0).toInt() },
                membershipLabel = membership?.firstString("title", "name"),
                membershipPrice = membership?.firstNumber("price") ?: 0.0,
                physicalProducts = lines(fields.arr("physical_products")),
                services = lines(fields.arr("services")),
                members = members,
                notes = notes,
                authorId = author?.let { (it.firstNumber("id") ?: 0.0).toInt() },
                authorName = author?.firstString("name"),
                isMultiplePayment = isMultiple,
                singlePayment = if (isMultiple) PaymentMethod.Cash
                else PaymentMethod.from(fields.firstString("single_payment")) ?: PaymentMethod.Cash,
                singleCardNumber = if (isMultiple) "" else fields.firstString("card_number") ?: "",
                paymentLegs = legs,
            )
        }

        private fun lines(raw: JSONArray?): List<ProductLine> =
            (0 until (raw?.length() ?: 0)).mapNotNull { index ->
                val obj = raw?.optJSONObject(index) ?: return@mapNotNull null
                val productId = obj.firstNumber("id") ?: return@mapNotNull null
                val title = obj.firstString("title", "name") ?: return@mapNotNull null
                ProductLine(
                    productId = productId.toInt(),
                    quantity = maxOf((obj.firstNumber("quantity") ?: 1.0).toInt(), 1),
                    title = title,
                    price = obj.firstNumber("price"),
                    imageUrl = obj.imageUrl(),
                )
            }
    }
}
