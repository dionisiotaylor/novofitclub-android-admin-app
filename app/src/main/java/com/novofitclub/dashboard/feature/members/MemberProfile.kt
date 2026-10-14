package com.novofitclub.dashboard.feature.members

import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class MemberAccountStatus(val raw: String, val label: String) {
    Active("active", "Activo"),
    Expired("expired", "Vencido");

    companion object {
        fun fromRaw(raw: String?): MemberAccountStatus? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * The complete choice list for the `membership_type` ACF select field (read
 * from its field-editor config in wp-admin) — [id] is what the API expects on
 * write, [label] is what it shows on read.
 */
data class MembershipTypeOption(val id: String, val label: String) {
    companion object {
        val all = listOf(
            MembershipTypeOption("ebb48c_regular", "Regular"),
            MembershipTypeOption("3cd4b8_sesion_dia_gym", "Sesión Día Gym"),
            MembershipTypeOption("b7a31d_sesion_prueba", "Sesión Prueba"),
            MembershipTypeOption("ce3258_clase_novo_dance_baile", "Clase Novo Dance Baile"),
            MembershipTypeOption("e46a1d_clase_funcional", "Clase Funcional"),
            MembershipTypeOption("8a09a6_adulto_mayor", "Adulto mayor"),
            MembershipTypeOption("1245a2_menor_de_edad", "Menor de edad"),
            MembershipTypeOption("c472fd_policia", "Policia"),
            MembershipTypeOption("1eb9e0_pole_dance", "Pole Dance"),
        )
    }
}

internal object MemberDateFormats {
    /** ACF `birthday` is "dd/MM/yyyy". */
    val birthday: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Every other ACF date field is ISO "yyyy-MM-dd". */
    val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parse(raw: String?, format: DateTimeFormatter): LocalDate? {
        if (raw.isNullOrEmpty()) return null
        return try {
            LocalDate.parse(raw, format)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/**
 * The editable side of a member, parsed from `wp/v2/member/{id}` (native WP
 * post type with an ACF field group). `status` reads back as
 * "active"/"expired" but the server's REST write validation only accepts
 * "pendiente"/"aprobado"/"denegado", so it's read-only here (writing it would
 * reject the whole PATCH). `fecha_admin` is deliberately not surfaced (admin
 * only). `membership_type` comes back as `{value, label}` but is written as
 * the raw value string.
 */
data class MemberProfile(
    val id: String,
    val memberId: String,
    val name: String,
    val phone: String,
    val email: String,
    val birthday: LocalDate?,
    val gender: String?,
    val status: MemberAccountStatus?,
    val membershipExpirationDate: LocalDate?,
    val paymentDay: LocalDate?,
    val lastPaymentDate: LocalDate?,
    val originalExpirationDate: LocalDate?,
    val membershipType: String?,
    private val membershipTypeRawLabel: String?,
) {
    val membershipTypeLabel: String?
        get() = MembershipTypeOption.all.firstOrNull { it.id == membershipType }?.label ?: membershipTypeRawLabel

    /**
     * Body for `PATCH wp/v2/member/{id}`: ACF updates nested under `acf`, plus
     * the post `title` kept in sync as "Name – member_id" (the server
     * regenerates `acf.name` from the title on every save, so writing the
     * name alone never sticks).
     */
    fun patchBody(): JSONObject {
        val trimmedName = name.trim()
        val acf = JSONObject()
            .put("name", trimmedName)
            .put("phone", phone.trim())
            .put("email", email.trim())
        birthday?.let { acf.put("birthday", it.format(MemberDateFormats.birthday)) }
        gender?.let { acf.put("gender", it) }
        membershipExpirationDate?.let { acf.put("membership_expiration_date", it.format(MemberDateFormats.iso)) }
        paymentDay?.let { acf.put("payment_day", it.format(MemberDateFormats.iso)) }
        lastPaymentDate?.let { acf.put("fecha_del_ultimo_pago", it.format(MemberDateFormats.iso)) }
        originalExpirationDate?.let { acf.put("fecha_original_vencimiento", it.format(MemberDateFormats.iso)) }
        membershipType?.let { acf.put("membership_type", it) }

        val body = JSONObject().put("acf", acf)
        if (memberId.isNotEmpty()) body.put("title", "$trimmedName – $memberId")
        return body
    }

    companion object {
        fun from(id: String, fields: JSONObject): MemberProfile {
            val acf = fields.obj("acf") ?: JSONObject()
            val type = acf.obj("membership_type")
            return MemberProfile(
                id = id,
                memberId = acf.firstString("id") ?: "",
                name = acf.firstString("name") ?: "",
                phone = acf.firstString("phone") ?: "",
                email = acf.firstString("email") ?: "",
                birthday = MemberDateFormats.parse(acf.firstString("birthday"), MemberDateFormats.birthday),
                gender = acf.firstString("gender"),
                status = MemberAccountStatus.fromRaw(acf.firstString("status")),
                membershipExpirationDate = MemberDateFormats.parse(acf.firstString("membership_expiration_date"), MemberDateFormats.iso),
                paymentDay = MemberDateFormats.parse(acf.firstString("payment_day"), MemberDateFormats.iso),
                lastPaymentDate = MemberDateFormats.parse(acf.firstString("fecha_del_ultimo_pago"), MemberDateFormats.iso),
                originalExpirationDate = MemberDateFormats.parse(acf.firstString("fecha_original_vencimiento"), MemberDateFormats.iso),
                membershipType = type?.firstString("value"),
                membershipTypeRawLabel = type?.firstString("label"),
            )
        }
    }
}

/**
 * Draft for `POST wp/v2/member`. Deliberately omits `acf.status` and every
 * membership date (set later by the server's daily cron / sale flow). The
 * post `title` is the plain name — a server hook appends "– {cédula}" after
 * save. `status: "publish"` is the native WP post status (otherwise WP
 * defaults to draft and the member is invisible everywhere).
 */
data class NewMemberDraft(
    val name: String = "",
    val cedula: String = "",
    val phone: String = "",
    val email: String = "",
    // 12 years old is the gym's minimum age — a more useful default than today.
    val birthday: LocalDate = LocalDate.now().minusYears(12),
    val gender: String = "male",
    val membershipType: String? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && cedula.isNotBlank()

    fun createBody(): JSONObject {
        val trimmedName = name.trim()
        val acf = JSONObject()
            .put("name", trimmedName)
            .put("id", cedula.trim())
            .put("gender", gender)
        phone.trim().takeIf { it.isNotEmpty() }?.let { acf.put("phone", it) }
        email.trim().takeIf { it.isNotEmpty() }?.let { acf.put("email", it) }
        acf.put("birthday", birthday.format(MemberDateFormats.birthday))
        membershipType?.let { acf.put("membership_type", it) }
        return JSONObject()
            .put("title", trimmedName)
            .put("status", "publish")
            .put("acf", acf)
    }
}
