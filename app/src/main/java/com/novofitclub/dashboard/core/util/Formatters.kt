package com.novofitclub.dashboard.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Same formatting rules as the iOS app's Formatters. */
object Formatters {
    private val spanish = Locale("es")

    // Plain decimal, no currency symbol: API values may already arrive with a symbol.
    private val decimal = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    fun money(value: Double?): String? = value?.let { decimal.format(it) }

    private val colonesFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))

    /** "₡24,950" — whole colones, as the reception screens show them. */
    fun colones(value: Double): String = "₡" + colonesFormat.format(value)

    private val parsers = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    /**
     * The plugins mix ISO8601 ("...T12:54:31"), MySQL datetime ("... 17:33:35")
     * and bare dates — this parses whichever one shows up.
     */
    fun parseDate(raw: String?): Date? {
        if (raw.isNullOrEmpty()) return null
        for (pattern in parsers) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            try {
                return format.parse(raw) ?: continue
            } catch (_: ParseException) {
            }
        }
        return null
    }

    /** "3 oct 2026, 14:32", falling back to the raw string when unparseable. */
    fun friendlyDate(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        return parseDate(raw)?.let { friendlyDate(it) } ?: raw
    }

    fun friendlyDate(date: Date): String = format("d MMM yyyy, HH:mm", date)

    /** "3 oct" */
    fun shortDate(date: Date): String = format("d MMM", date)

    /** "16 de septiembre" — day-group heading in Cierres. */
    fun dayTitle(date: Date): String = format("d 'de' MMMM", date)

    /** "14:32" */
    fun timeOnly(date: Date): String = format("HH:mm", date)

    /** "Septiembre 2026" — Cierres month filter. */
    fun monthTitle(date: Date): String =
        format("LLLL yyyy", date).replaceFirstChar { it.titlecase(spanish) }

    /** "2026-10-03" — the format the WP endpoints expect for date params. */
    fun apiDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    private fun format(pattern: String, date: Date) = SimpleDateFormat(pattern, spanish).format(date)
}
