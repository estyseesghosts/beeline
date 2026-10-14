package me.foxtails.palustris.domain

/**
 * JavaScript-form expressions as Misskey stores them: `/source/flags`. Stateless.
 *
 * Misskey checks expressions with RE2 on the server and JavaScript or RE2 when it matches. Beeline compiles
 * them with Java, which accepts more syntax than RE2. The server stays the judge of what it stores.
 */
object FilterExpressions {
    private val stored = Regex("^/(.+)/(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** Splits a stored `/source/flags` value, or null when it does not have that form. Same rule as Misskey. */
    fun parse(value: String): FilterPattern.Expression? =
        stored.matchEntire(value)?.let { FilterPattern.Expression(it.groupValues[1], it.groupValues[2]) }

    fun format(expression: FilterPattern.Expression): String = "/${expression.source}/${expression.flags}"

    /**
     * Compiles [source] with JavaScript [flags]. `i`, `m`, and `s` map to Kotlin options. `g`, `y`, `d`, `u`, and `v`
     * do not change a containment test. Any other flag, a repeated flag, or a syntax error gives null.
     */
    fun compile(source: String, flags: String): Regex? {
        if (source.isEmpty() || flags.toSet().size != flags.length || flags.any { it !in JS_FLAGS }) return null
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        // JavaScript `i` folds every Unicode letter; Java needs UNICODE_CASE for that.
        val unicodeCase = if ('i' in flags) "(?u)" else ""
        return runCatching { Regex(unicodeCase + source, options) }.getOrNull()
    }

    /** The literal text of [source] when every character outside a backslash escape is ordinary, or null. */
    fun literalOrNull(source: String): String? {
        val out = StringBuilder()
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char == '\\') {
                val next = source.getOrNull(index + 1) ?: return null
                if (next !in SPECIAL) return null
                out.append(next)
                index += 2
            } else {
                if (char in SPECIAL) return null
                out.append(char)
                index += 1
            }
        }
        return out.toString()
    }

    /** Escapes [literal] so that it matches itself in JavaScript, RE2, and Java. */
    fun escapeLiteral(literal: String): String = buildString {
        literal.forEach { char ->
            if (char in SPECIAL) append('\\')
            append(char)
        }
    }

    private const val SPECIAL = "\\^$.|?*+()[]{}/"
    private const val JS_FLAGS = "gimsuyvd"
}
