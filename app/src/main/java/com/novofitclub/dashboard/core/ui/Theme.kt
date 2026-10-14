package com.novofitclub.dashboard.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Color and type tokens shared with the iOS app (sampled from the NOVO FIT
 * CLUB Figma file): dark charcoal surfaces, a crimson accent, black/heavy
 * headings.
 */
object NovoColors {
    val background = Color(0xFF1E1E1E)
    val card = Color(0xFF2C2C2E)
    val panel = Color(0xFF171818)
    val accent = Color(0xFFE00E46)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF747474)
    val separator = Color.White.copy(alpha = 0.08f)

    // Membership status colors (current / about to lapse / expired).
    val statusActive = Color(0xFF34C759)
    val statusExpiring = Color(0xFFFF9F0A)
    val statusExpired = Color(0xFFFF3B30)

    // Sale status that isn't pass/fail (e.g. "trade").
    val statusNeutral = Color(0xFF5AC8FA)

    // Member avatar backgrounds, rotated by list position.
    val avatarPalette = listOf(
        Color(0xFF3D4A78),
        Color(0xFF2E5F63),
        Color(0xFF7A4736),
        Color(0xFF6B3B5E),
        Color(0xFF3F5A3A),
    )

    // Dashboard "clients per plan" breakdown — one per membership plan.
    val planPalette = listOf(
        Color(0xFFE8A87C),
        Color(0xFFB08D28),
        Color(0xFF3BB6A6),
        Color(0xFF3D4A78),
        Color(0xFF8E44AD),
        Color(0xFFC94F6D),
        Color(0xFF5AC8FA),
        Color(0xFF9B7ED9),
        Color(0xFFE0932E),
    )
}

object NovoDimens {
    val cornerRadius = 18.dp
    val controlHeight = 52.dp
}

object NovoFonts {
    fun heading(size: Int) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Black, color = NovoColors.textPrimary)
    fun title(size: Int) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.ExtraBold, color = NovoColors.textPrimary)
    fun semibold(size: Int) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = NovoColors.textPrimary)
    fun body(size: Int = 15) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Normal, color = NovoColors.textPrimary)
    fun medium(size: Int = 15) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium, color = NovoColors.textPrimary)
}

/** First letter of the first two words — shared by every avatar-style row. */
fun initials(name: String): String {
    val letters = name.split(" ").filter { it.isNotEmpty() }.take(2).map { it.first() }
    return if (letters.isEmpty()) "?" else letters.joinToString("").uppercase()
}

/**
 * Several `sale-creator` endpoints embed the club's member_id in a name field
 * as "Real Name – member_id" (or just "– member_id"). Splits that apart;
 * either half can be null.
 */
fun splitEmbeddedId(raw: String): Pair<String?, String?> {
    val dash = raw.indexOf('–')
    if (dash < 0) return raw.trim().ifEmpty { null } to null
    val name = raw.substring(0, dash).trim().ifEmpty { null }
    val id = raw.substring(dash + 1).trim().ifEmpty { null }
    return name to id
}

@Composable
fun NovoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NovoColors.accent,
            onPrimary = Color.White,
            background = NovoColors.background,
            onBackground = NovoColors.textPrimary,
            surface = NovoColors.panel,
            onSurface = NovoColors.textPrimary,
            surfaceVariant = NovoColors.card,
            onSurfaceVariant = NovoColors.textSecondary,
            secondaryContainer = NovoColors.accent.copy(alpha = 0.18f),
            onSecondaryContainer = NovoColors.accent,
            outline = NovoColors.separator,
        ),
        content = content,
    )
}
