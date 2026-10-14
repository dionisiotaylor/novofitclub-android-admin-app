package com.novofitclub.dashboard.core.net

import org.json.JSONArray
import org.json.JSONObject

/*
 * Helpers mirroring the iOS JSONValue extensions. The plugins have no
 * published schema and mix Spanish/English key names, so screens look up a
 * value by several candidate keys.
 */

/** String value of [key], or null when missing, JSON null, empty, or a nested object/array. */
fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is String -> v.ifEmpty { null }
        is Boolean -> v.toString()
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
        }
        else -> null
    }
}

/** Numeric value of [key]; also accepts numeric strings ("1250.00"). */
fun JSONObject.num(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.trim().replace(",", "").toDoubleOrNull()
        else -> null
    }
}

fun JSONObject.bool(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.lowercase().toBooleanStrictOrNull()
        else -> null
    }
}

fun JSONObject.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { str(it) }

fun JSONObject.firstNumber(vararg keys: String): Double? = keys.firstNotNullOfOrNull { num(it) }

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

/**
 * An item's photo comes back either as a plain URL string or as a WP-style
 * media object ({url|src|source_url}).
 */
fun JSONObject.imageUrl(): String? =
    str("image") ?: obj("image")?.firstString("url", "src", "source_url")

/** Human-readable single-line rendering, used by generic detail rows. */
fun Any?.displayString(): String = when (this) {
    null, JSONObject.NULL -> "—"
    is JSONArray -> (0 until length()).joinToString(", ") { opt(it).displayString() }
    is JSONObject -> keys().asSequence().joinToString(", ") { "$it: ${opt(it).displayString()}" }
    is Double -> if (this == Math.floor(this)) toLong().toString() else toString()
    else -> toString()
}

/** Shallow copy, handy before mutating a record for a PUT/PATCH body. */
fun JSONObject.copy(): JSONObject = JSONObject(toString())
