package com.vanderck.xtreamtv.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

// Xtream Codes panels are a family of forks, not one server, and they disagree
// about types: ids arrive as `1` or `"1"`, ratings as `7.5`, `"7.5"`, or `""`,
// and empty collections as `[]` where an object was expected. Every model
// decodes through these helpers, so one odd field never costs the whole list.

/** The value as a non-empty, trimmed string, whatever JSON type it arrived as. */
fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val raw = when (val v = opt(key)) {
        is String -> v
        is Boolean -> if (v) "1" else "0"
        is Int, is Long -> v.toString()
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
        }
        else -> return null
    }
    val trimmed = raw.trim()
    return if (trimmed.isEmpty() || trimmed == "null") null else trimmed
}

fun JSONObject.dbl(key: String): Double? = str(key)?.toDoubleOrNull()

fun JSONObject.int(key: String): Int? {
    val text = str(key) ?: return null
    return text.toIntOrNull() ?: text.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
}

fun JSONObject.bool(key: String): Boolean? = when (val t = str(key)?.lowercase()) {
    null -> null
    "1", "true", "yes" -> true
    "0", "false", "no" -> false
    else -> t.toIntOrNull()?.let { it != 0 }
}

/** A string or an array of strings, as `backdrop_path` arrives either way. */
fun JSONObject.strList(key: String): List<String> {
    val v = opt(key)
    if (v is JSONArray) {
        return (0 until v.length()).mapNotNull { i ->
            when (val e = v.opt(i)) {
                is String -> e.trim().takeIf { it.isNotEmpty() }
                is Number -> e.toString()
                else -> null
            }
        }
    }
    return str(key)?.let { listOf(it) } ?: emptyList()
}

/** The object at `key`, or null when the panel sent `[]`, `""` or nothing. */
fun JSONObject.obj(key: String): JSONObject? = opt(key) as? JSONObject

/** Artwork URLs from panels are often unescaped; try hard before giving up. */
fun lenientUrl(text: String?): String? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) return null
    return try {
        URI(trimmed).toASCIIString()
    } catch (_: Exception) {
        // Escape the characters that commonly break URIs, keeping the structure.
        buildString {
            for (c in trimmed) {
                when {
                    c == ' ' -> append("%20")
                    c.code > 127 || c in "\"<>\\^`{|}" ->
                        append(URLEncoder.encode(c.toString(), "UTF-8"))
                    else -> append(c)
                }
            }
        }
    }
}

/** Whether a body is an empty JSON value, which panels send for empty catalogs. */
fun isEmptyJson(body: String): Boolean {
    val head = body.take(16).trim()
    return head.isEmpty() || head == "[]" || head == "{}" || head == "null"
}
