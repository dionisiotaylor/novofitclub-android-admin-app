package com.novofitclub.dashboard.feature.members

import androidx.compose.ui.graphics.Color
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.core.util.Formatters
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

enum class MembershipStatus {
    Active, Expiring, Expired;

    val color: Color
        get() = when (this) {
            Active -> NovoColors.statusActive
            Expiring -> NovoColors.statusExpiring
            Expired -> NovoColors.statusExpired
        }

    /** Single-word label used by the detail screen's status capsule. */
    val word: String
        get() = when (this) {
            Active -> "Activo"
            Expiring -> "Por vencer"
            Expired -> "Vencido"
        }
}

enum class MembersFilter(val label: String, val status: MembershipStatus?) {
    All("Todos", null),
    Active("Activos", MembershipStatus.Active),
    Expiring("Por vencer", MembershipStatus.Expiring),
    Expired("Vencidos", MembershipStatus.Expired),
}

/**
 * A member row parsed from the raw JSON record returned by
 * `sale-creator/v1/members`: `id`, `name`, `member_id`, `phone`, `email`,
 * `age`, `membership_expiration_date`. There's no explicit status field —
 * status is computed from the expiration date. `name` embeds the club's
 * member_id ("Aaron Abilio Camacho – 115250159" or just "– 305660136"),
 * which [cleanName] strips off.
 */
data class Member(
    val id: String,
    val memberId: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val status: MembershipStatus,
    val statusLabel: String,
    val initials: String,
    val avatarColor: Color,
    /** Thumbnail of the turnstile access photo, when there is one. */
    val photoUrl: String? = null,
    /** From the birthday, server-side; null without one. A sale's age rule reads it. */
    val age: Int? = null,
    /** The membership type's label ("Regular", "Estudiante"…). */
    val plan: String? = null,
    val expiration: LocalDate? = null,
) {
    /** Search keys, computed once: the name folded (no case or accents), the cédula and phone as digits. */
    val searchName: String = foldForSearch(name)
    val cedulaDigits: String = memberId.filter(Char::isLetterOrDigit).uppercase()
    val phoneDigits: String = phone.orEmpty().filter(Char::isDigit)

    /** The list's short status: "Activo", "Vence hoy", "Vence en 3d", "Vencido". */
    val chipLabel: String
        get() = when (status) {
            MembershipStatus.Active -> "Activo"
            MembershipStatus.Expired -> "Vencido"
            MembershipStatus.Expiring -> expiration?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
                ?.let { if (it <= 0L) "Vence hoy" else "Vence en ${it}d" } ?: "Por vencer"
        }

    companion object {
        private const val EXPIRING_WINDOW_DAYS = 7

        fun from(fields: JSONObject, fallbackIndex: Int): Member {
            val id = fields.str("id") ?: "row-$fallbackIndex"
            val memberId = fields.firstString("member_id") ?: id
            val name = cleanName(
                fields.firstString("name", "nombre", "display_name", "full_name") ?: "",
                memberId,
            )
            val (status, label) = resolveStatus(fields)
            val expiration = parseExpiration(fields.firstString("membership_expiration_date"))
            return Member(
                id = id,
                memberId = memberId,
                name = name,
                phone = fields.firstString("phone", "telefono", "celular", "whatsapp"),
                email = fields.firstString("email", "correo"),
                status = status,
                statusLabel = label,
                initials = initials(name),
                avatarColor = NovoColors.avatarPalette[fallbackIndex % NovoColors.avatarPalette.size],
                photoUrl = fields.firstString("photo_url"),
                age = fields.num("age")?.toInt(),
                plan = fields.firstString("membership_type")?.ifBlank { null },
                expiration = expiration,
            )
        }

        /**
         * For opening a member from a screen that only has its post id (a
         * sale's member row), via `wp/v2/member/{id}`. That endpoint carries
         * the same values as `sale-creator/v1/members`, just nested under
         * `acf` (with the cédula as `acf.id`), so they're re-keyed into that shape.
         * It has no access photo, so the caller passes the one it already has.
         */
        fun fromWpMember(postId: String, wpFields: JSONObject, fallbackIndex: Int, photoUrl: String?): Member {
            val acf = wpFields.optJSONObject("acf") ?: JSONObject()
            val fields = JSONObject()
                .put("id", postId)
                .put("name", acf.opt("name"))
                .put("member_id", acf.opt("id"))
                .put("phone", acf.opt("phone"))
                .put("email", acf.opt("email"))
                .put("membership_expiration_date", acf.opt("membership_expiration_date"))
                .put("photo_url", photoUrl)
            return from(fields, fallbackIndex)
        }

        private val ISO_DAY = Regex("""^\d{4}-\d{2}-\d{2}""")

        /** "2026-11-16" straight (the members index, ~3700 at once); other formats through [Formatters.parseDate]. */
        private fun parseExpiration(raw: String?): LocalDate? {
            if (raw.isNullOrEmpty()) return null
            if (ISO_DAY.containsMatchIn(raw)) runCatching { return LocalDate.parse(raw.take(10)) }
            return Formatters.parseDate(raw)?.toLocalDate()
        }

        /** Strips the trailing " – <member_id>"; placeholder when no real name is on file. */
        private fun cleanName(raw: String, fallbackId: String): String =
            splitEmbeddedId(raw).first ?: "Miembro #$fallbackId"

        private fun resolveStatus(fields: JSONObject): Pair<MembershipStatus, String> {
            val expiration = parseExpiration(
                fields.firstString(
                    "membership_expiration_date", "expiration_date", "vencimiento", "fecha_vencimiento",
                    "membership_expires", "expires_at", "fecha_fin", "next_due_date",
                ),
            )
            val raw = fields.firstString("membership_status", "status", "estado", "membership")
            return status(raw, expiration)
        }

        /** Shared with the edit screen's post-save refresh. */
        fun status(explicit: String?, expiration: LocalDate?): Pair<MembershipStatus, String> {
            if (explicit != null) {
                val normalized = Normalizer.normalize(explicit, Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}+"), "")
                    .lowercase()
                if (normalized.contains("por venc") || normalized.contains("expiring") || normalized.contains("proximo")) {
                    return MembershipStatus.Expiring to explicit
                }
                if (normalized.contains("venc") || normalized.contains("expired") || normalized.contains("inactiv")) {
                    // The stored status is the English "expired": say it in Spanish.
                    val label = if (normalized == "expired") {
                        expiration?.takeIf { it.isBefore(LocalDate.now()) }?.let { statusFromExpiration(it).second } ?: "Vencido"
                    } else {
                        explicit
                    }
                    return MembershipStatus.Expired to label
                }
            }
            if (expiration != null) return statusFromExpiration(expiration)
            return MembershipStatus.Active to "Activo"
        }

        fun statusFromExpiration(date: LocalDate): Pair<MembershipStatus, String> {
            val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
            if (days < 0) {
                val elapsed = -days
                return MembershipStatus.Expired to "Venció hace $elapsed día${if (elapsed == 1L) "" else "s"}"
            }
            if (days <= EXPIRING_WINDOW_DAYS) {
                return MembershipStatus.Expiring to
                    if (days == 0L) "Vence hoy" else "Vence en $days día${if (days == 1L) "" else "s"}"
            }
            return MembershipStatus.Active to "Activo · vence ${Formatters.shortDate(date.toDate())}"
        }
    }
}

/** Lowercase without accents, for matching names as typed ("maria" finds "María"). */
internal fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

internal fun Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

internal fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
