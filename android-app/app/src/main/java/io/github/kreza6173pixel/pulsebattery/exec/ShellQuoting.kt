package io.github.kreza6173pixel.pulsebattery.exec

/**
 * POSIX shell argument quoting. Pure Kotlin, no Android — unit-tested.
 *
 * Everything user-supplied must go through [quote]. Never build a command by concatenating
 * user input: `quote("a b")` is the only safe way to get an `a b` argument across.
 */
object ShellQuoting {

    /**
     * Wraps [arg] in single quotes so the shell treats it as exactly one literal argument.
     *
     * Inside single quotes every character is literal — spaces, `$`, backticks, newlines,
     * `*`, `;` — so nothing can be reinterpreted as syntax. The only character that cannot
     * appear is the single quote itself, which is emitted by closing the quote, adding a
     * backslash-escaped quote, and reopening: `'` becomes `'\''`.
     *
     * Quoting is unconditional. A "quote only if it looks unsafe" optimisation would leave
     * user input sitting unquoted in a command line, and auditing which characters are safe
     * is exactly the kind of assumption that turns into a shell injection later. Always
     * quoting means the result is safe by construction and the tests can assert that every
     * input comes back wrapped.
     */
    fun quote(arg: String): String {
        val body = arg.replace("'", "'\\''")
        return "'$body'"
    }

    /**
     * Quotes every element of [args] and joins them with single spaces. Each element stays a
     * separate argument, so `listOf("a b", "c")` becomes `'a b' 'c'`.
     */
    fun joinArgs(args: List<String>): String = args.joinToString(" ") { quote(it) }
}