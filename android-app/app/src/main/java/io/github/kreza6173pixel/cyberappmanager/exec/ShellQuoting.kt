package io.github.kreza6173pixel.cyberappmanager.exec

/**
 * POSIX shell argument quoting. Pure Kotlin, no Android, unit-tested.
 *
 * Everything user-supplied (package names, component names, paths) must go through [quote].
 * Never build a command by concatenating user input.
 */
object ShellQuoting {

    /**
     * Wraps [arg] in single quotes so the shell treats it as exactly one literal argument.
     * The only character that cannot appear inside single quotes is the single quote itself,
     * which is emitted by closing the quote, adding a backslash-escaped quote, and reopening.
     * Quoting is unconditional, so the result is safe by construction.
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
