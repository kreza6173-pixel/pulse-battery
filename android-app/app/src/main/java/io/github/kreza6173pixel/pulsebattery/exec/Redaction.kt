package io.github.kreza6173pixel.pulsebattery.exec

/**
 * Masks things that look like secrets before anything is shown on the console screen.
 * Pure Kotlin, no Android — unit-tested.
 *
 * This is a display filter only. It is deliberately conservative: it replaces the *value*
 * of an assignment whose key looks sensitive, and it does not try to be clever about
 * anything else, because a filter that tries to be clever about secrets misses them.
 */
object Redaction {

    const val MASK = "***REDACTED***"

    /** Key names whose assigned value is always masked, matched case-insensitively. */
    private val SENSITIVE_KEYS = listOf(
        "password", "passwd", "pwd", "secret", "token", "apikey", "api_key", "api-key",
        "accesskey", "access_key", "access-key", "privatekey", "private_key", "private-key",
        "auth", "authorization", "bearer", "session", "cookie", "credential", "credentials",
        "passphrase", "otp", "pin",
    )

    /** `KEY=VALUE`, `KEY: VALUE` and `export KEY=VALUE`, with KEY being a shell-ish name. */
    private val ASSIGNMENT =
        Regex("""(?<key>[A-Za-z_][A-Za-z0-9_.\-]*)(?<sep>\s*[=:]\s*)(?<val>[^\s'"]+)""")

    private val QUOTED_VALUE =
        Regex("""(?<key>[A-Za-z_][A-Za-z0-9_.\-]*)(?<sep>\s*[=:]\s*)(?<val>"[^"]*"|'[^']*')""")

    /**
     * An `Authorization:` header carries the credential across the rest of the line
     * (`Authorization: Bearer eyJhbGci...`), not just the next whitespace-delimited token.
     * This rule therefore masks to end of line, and must run before the generic rules.
     */
    private val AUTH_HEADER =
        Regex("""(?i)\b((?:proxy-)?authorization)(\s*[:=]\s*)[^\r\n]+""")

    private fun isSensitiveKey(key: String): Boolean {
        val k = key.lowercase()
        return SENSITIVE_KEYS.any { k == it || k.endsWith("_$it") || k.endsWith("-$it") || k.endsWith(it) }
    }

    /**
     * Returns [text] with sensitive assignment values replaced by [MASK].
     * The original string is never mutated and the input may be any size.
     *
     * Deliberately over-masks: once a sensitive key is seen, everything to the end of the
     * value is hidden. Under-masking a secret is unrecoverable; hiding one extra token is not.
     */
    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = AUTH_HEADER.replace(text) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}$MASK"
        }
        out = QUOTED_VALUE.replace(out) { m ->
            val key = m.groups["key"]!!.value
            if (isSensitiveKey(key)) {
                "$key${m.groups["sep"]!!.value}\"$MASK\""
            } else {
                m.value
            }
        }
        out = ASSIGNMENT.replace(out) { m ->
            val key = m.groups["key"]!!.value
            if (isSensitiveKey(key)) {
                "$key${m.groups["sep"]!!.value}$MASK"
            } else {
                m.value
            }
        }
        return out
    }

    /** Convenience for a whole command line shown in the history list. */
    fun redactCommand(command: String): String = redact(command)
}