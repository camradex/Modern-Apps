package com.vayunmathur.messages.gvoice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Lenient parser for the cookie blobs users paste at setup time.
 *
 * Accepts three formats:
 *   1. A JSON object: `{"SID":"...","HSID":"..."}` — possibly with extra
 *      whitespace or trailing commas (we don't insist on strict JSON).
 *   2. A cURL command from the browser devtools "Copy as cURL" feature.
 *      We pull cookies out of the `-H 'Cookie: …'` (or `-b '…'`) flag
 *      and ignore everything else.
 *   3. A raw Cookie header value: `SID=…; HSID=…; …`
 *
 * Returns the recognized cookie subset (intersection of what's found
 * and the set of cookies Voice actually uses). Returns null when no
 * cookies could be extracted at all — the UI surfaces a validation
 * error in that case.
 */
object CookieParser {

    private val recognized: Set<String> =
        (VoiceEndpoints.RequiredCookies + VoiceEndpoints.OptionalCookies).toSet()

    fun parse(input: String): Map<String, String>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        return parseJsonObject(trimmed)
            ?: parseNetscapeCookiesTxt(trimmed)
            ?: parseCurl(trimmed)
            ?: parseRawCookieHeader(trimmed)
    }

    /** Validate that all required cookies are present + non-empty. */
    fun missingRequired(cookies: Map<String, String>): List<String> =
        VoiceEndpoints.RequiredCookies.filter { cookies[it].isNullOrBlank() }

    // ----------------------------------------------------------------
    // Format-specific parsers
    // ----------------------------------------------------------------

    private fun parseJsonObject(input: String): Map<String, String>? {
        if (!input.startsWith("{")) return null
        val obj: JsonObject = try {
            Json.decodeFromString(JsonObject.serializer(), input)
        } catch (_: Throwable) {
            // Tolerant JSON: try a one-pass cleanup (strip trailing commas).
            val cleaned = input.replace(Regex(",\\s*([}\\]])"), "$1")
            try {
                Json.decodeFromString(JsonObject.serializer(), cleaned)
            } catch (_: Throwable) {
                return null
            }
        }
        val out = mutableMapOf<String, String>()
        for ((key, value) in obj) {
            if (key !in recognized) continue
            val v = (value as? JsonPrimitive)?.contentOrNull ?: continue
            if (v.isNotBlank()) out[key] = v
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun parseCurl(input: String): Map<String, String>? {
        if (!input.contains("curl")) return null
        // Match `-H 'Cookie: ...'` or `-H "Cookie: ..."` (case-insensitive
        // header name; many curl exports use Cookie or cookie) and the
        // `-b '...'` short form.
        val patterns = listOf(
            Regex("""-H\s+['"](?:cookie|Cookie):\s*([^'"]+)['"]"""),
            Regex("""-b\s+['"]([^'"]+)['"]"""),
        )
        for (p in patterns) {
            val match = p.find(input) ?: continue
            return parseRawCookieHeader(match.groupValues[1])
        }
        return null
    }

    private fun parseRawCookieHeader(input: String): Map<String, String>? {
        // Don't match obvious non-cookie text. Required form: at least
        // one `key=value` pair separated by `;`.
        if (!input.contains('=')) return null
        val out = mutableMapOf<String, String>()
        for (part in input.split(';')) {
            val eq = part.indexOf('=')
            if (eq < 0) continue
            val key = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim()
            if (key.isEmpty() || value.isEmpty()) continue
            if (key !in recognized) continue
            out[key] = value
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * Parse Netscape's cookies.txt format (what the popular
     * "cookies.txt" / "cookies.txt LOCALLY" / "Get cookies.txt"
     * extensions all emit). Lines look like:
     *
     *     # Netscape HTTP Cookie File
     *     .google.com\tTRUE\t/\tTRUE\t1780000000\tSID\tabc123...
     *
     * Seven tab-separated fields per cookie line:
     *   1. domain
     *   2. includeSubdomains (TRUE/FALSE)
     *   3. path
     *   4. secure (TRUE/FALSE)
     *   5. expiration (unix seconds)
     *   6. name
     *   7. value
     *
     * Comment lines (starting with `#`) and blank lines are ignored.
     */
    private fun parseNetscapeCookiesTxt(input: String): Map<String, String>? {
        // Heuristic: there must be at least one line with 7 tab-separated
        // fields AND a recognized cookie name in field 6.
        val out = mutableMapOf<String, String>()
        var sawAnyTabLine = false
        for (line in input.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val fields = line.split('\t')
            if (fields.size < 7) continue
            sawAnyTabLine = true
            val name = fields[5].trim()
            val value = fields[6].trim()
            if (name.isEmpty() || value.isEmpty()) continue
            if (name !in recognized) continue
            out[name] = value
        }
        if (!sawAnyTabLine) return null
        return out.takeIf { it.isNotEmpty() }
    }
}
