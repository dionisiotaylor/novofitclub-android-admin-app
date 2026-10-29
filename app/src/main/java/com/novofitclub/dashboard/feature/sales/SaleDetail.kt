package com.novofitclub.dashboard.feature.sales

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.imageUrl
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.core.util.Formatters
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class SaleMemberRef(
    /** The member's post id — what `wp/v2/member/{id}` takes. */
    val id: String,
    val name: String,
    val memberIdLabel: String?,
    val initials: String,
    val avatarColor: Color,
    val photoUrl: String?,
    /** Index into the avatar palette, so the member screen it opens keeps the same color. */
    val paletteIndex: Int,
)

data class SaleLineItem(
    val id: String,
    val title: String,
    val price: Double?,
    val quantity: Int,
    val imageUrl: String?,
)

/**
 * One leg of a sale's payment. Most sales have exactly one; a "pago
 * combinado" (`payment: "multiple_payment"`) splits the total across two or
 * more of these, each with its own amount and, for card legs, its own last-4.
 */
data class SalePayment(
    val label: String,
    val icon: ImageVector,
    val amount: Double?,
    val cardMask: String?,
)

/**
 * Parsed from `sale-creator/v1/sales/{id}`: `id`, `status`, `payment`/
 * `single_payment`/`card_number`/`multiple_payment`, `membership` (`null`
 * for a pure physical-product sale), `physical_products`/`services`,
 * `members` (`[{id, name, photo_url}]`, `name` embedding the member_id), `notes`,
 * `author` (`{id, name}`), `post_date`.
 */
class SaleDetail(val id: String, fields: JSONObject) {
    val statusLabel: String
    val statusColor: Color
    val productLabel: String
    val productTitle: String
    val amount: Double?
    val imageUrl: String?
    val dateLabel: String?
    val members: List<SaleMemberRef>
    val payments: List<SalePayment>
    val author: String?
    val physicalProducts: List<SaleLineItem>
    val services: List<SaleLineItem>
    val ajustes: List<SaleAjuste>
    val notes: List<String>
    /**
     * Paid by canje (status "trade"): nothing was charged, so [amount] is 0
     * (as in the wp-admin Sales list) and [canjeValue] is what was given.
     */
    val isCanje: Boolean
    val canjeValue: Double

    /** Shown purple in the lists, like the wp-admin Sales list. */
    val hasAjuste: Boolean get() = ajustes.isNotEmpty()

    init {
        val (label, color) = statusInfo(fields.firstString("status"))
        statusLabel = label
        statusColor = color
        isCanje = fields.firstString("status")?.lowercase() == "trade"

        dateLabel = Formatters.friendlyDate(fields.firstString("post_date", "date_created"))

        val membership = fields.obj("membership")
        val membershipTitle = membership?.firstString("title", "name")
        val membershipPrice = membership?.firstNumber("price") ?: 0.0

        physicalProducts = lineItems(fields.arr("physical_products"))
        services = lineItems(fields.arr("services"))

        val productsTotal = physicalProducts.sumOf { (it.price ?: 0.0) * it.quantity }
        val servicesTotal = services.sumOf { it.price ?: 0.0 }
        ajustes = fields.arr("ajustes")?.objects().orEmpty().map { SaleAjuste.from(it) }
        val itemsTotal = membershipPrice + productsTotal + servicesTotal + ajustes.sumOf { it.amount }
        canjeValue = if (isCanje) itemsTotal else 0.0
        amount = if (isCanje) 0.0 else itemsTotal

        when {
            membershipTitle != null -> {
                productLabel = "MEMBRESÍA"; productTitle = membershipTitle
            }
            physicalProducts.isNotEmpty() -> {
                productLabel = "PRODUCTO"; productTitle = physicalProducts.first().title
            }
            services.isNotEmpty() -> {
                productLabel = "SERVICIO"; productTitle = services.first().title
            }
            ajustes.isNotEmpty() -> {
                productLabel = "DIFERENCIA"; productTitle = "Diferencia: ${ajustes.first().concept}"
            }
            else -> {
                productLabel = "VENTA"; productTitle = "Venta"
            }
        }

        // The item's own photo is what should show, not the membership's
        // (which is almost always empty).
        imageUrl = physicalProducts.firstOrNull()?.imageUrl ?: membership?.imageUrl()

        val membersArray = fields.arr("members")
        members = (0 until (membersArray?.length() ?: 0)).mapNotNull { index ->
            val obj = membersArray?.optJSONObject(index) ?: return@mapNotNull null
            val (name, memberId) = splitEmbeddedId(obj.firstString("name") ?: "")
            val displayName = name ?: "Miembro"
            SaleMemberRef(
                id = obj.firstString("id") ?: "$index",
                name = displayName,
                memberIdLabel = memberId?.let { "Cédula $it" },
                initials = initials(displayName),
                avatarColor = NovoColors.avatarPalette[index % NovoColors.avatarPalette.size],
                photoUrl = obj.firstString("photo_url"),
                paletteIndex = index,
            )
        }

        payments = payments(fields)
        author = fields.obj("author")?.firstString("name")

        val notesArray = fields.arr("notes")
        notes = (0 until (notesArray?.length() ?: 0)).mapNotNull { index ->
            when (val value = notesArray?.opt(index)) {
                is String -> value.ifEmpty { null }
                is JSONObject -> value.firstString("note", "text", "content", "body")
                else -> null
            }
        }
    }

    private companion object {
        fun statusInfo(raw: String?): Pair<String, Color> = when (raw?.lowercase()) {
            "completed" -> "Completada" to NovoColors.statusActive
            "pending" -> "Pendiente" to NovoColors.statusExpiring
            "cancelled", "canceled" -> "Cancelada" to NovoColors.statusExpired
            "trade" -> "Canje" to NovoColors.canje
            else -> (raw?.capitalizedWords() ?: "—") to NovoColors.textSecondary
        }

        /**
         * Empty when there's genuinely nothing to show (e.g. a "trade" has
         * `single_payment: null` and no `multiple_payment` entries). A combined
         * payment yields one entry per leg with its own amount and card mask;
         * a plain sale yields a single entry with no amount shown.
         */
        fun payments(fields: JSONObject): List<SalePayment> {
            val legs = fields.arr("multiple_payment")
            if (legs != null && legs.length() > 0) {
                return (0 until legs.length()).mapNotNull { index ->
                    val obj = legs.optJSONObject(index) ?: return@mapNotNull null
                    val method = obj.firstString("payment") ?: return@mapNotNull null
                    val (label, icon) = methodInfo(method)
                    SalePayment(label, icon, obj.firstNumber("amount"), obj.firstString("card_number"))
                }
            }
            val method = fields.firstString("single_payment") ?: return emptyList()
            val (label, icon) = methodInfo(method)
            return listOf(SalePayment(label, icon, null, fields.firstString("card_number")))
        }

        fun methodInfo(raw: String): Pair<String, ImageVector> = when (raw.lowercase()) {
            "card" -> "Tarjeta" to Icons.Outlined.CreditCard
            "cash", "efectivo" -> "Efectivo" to Icons.Outlined.Payments
            "sinpe", "sinpe_movil" -> "SINPE Móvil" to Icons.Outlined.PhoneIphone
            else -> raw.capitalizedWords() to Icons.Outlined.CreditCard
        }

        fun lineItems(raw: JSONArray?): List<SaleLineItem> =
            (0 until (raw?.length() ?: 0)).mapNotNull { index ->
                val obj = raw?.optJSONObject(index) ?: return@mapNotNull null
                val title = obj.firstString("title", "name") ?: return@mapNotNull null
                SaleLineItem(
                    id = obj.firstString("id") ?: "$index",
                    title = title,
                    price = obj.firstNumber("price"),
                    quantity = maxOf((obj.firstNumber("quantity") ?: 1.0).toInt(), 1),
                    imageUrl = obj.imageUrl(),
                )
            }
    }
}

/** Swift's `String.capitalized`: first letter of each word upper, rest lower. */
internal fun String.capitalizedWords(): String =
    split(" ").joinToString(" ") { word ->
        word.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
