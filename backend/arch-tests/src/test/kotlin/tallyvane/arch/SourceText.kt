package tallyvane.arch

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * This file's source with every comment removed and every literal kept.
 *
 * A rule that scans raw text otherwise reads its own prohibition out of the
 * KDoc explaining it: `Clock.kt` documents why `kotlin.time.Clock.System` is
 * banned and was flagged by `no-ambient-time` for saying so. Precise
 * documentation is not a violation, so every text-scanning rule reads this
 * instead of [KoFileDeclaration.text].
 *
 * String literals stay, because rules depend on them: `own-schema-only` reads
 * schema names out of `"jobs.job"`, and `no-sql-concat` matches a quoted
 * `SELECT`. A banned call named inside a string is still worth flagging.
 */
internal fun KoFileDeclaration.codeText(): String = codeWithoutComments(text)

/**
 * Removes line comments, block comments and KDoc from [source], leaving string
 * and character literals untouched.
 *
 * Each comment becomes one space rather than nothing, so that `Instant/**/.now`
 * does not collapse into the very marker the comment interrupted.
 *
 * This is a lexer, not a parser, and it stops short in one place: a comment
 * inside a string template's `${'$'}{ }` expression is treated as part of the
 * literal. Konsist offers no comment-free view of a file, and a full Kotlin
 * lexer here would be a second parser to maintain.
 */
internal fun codeWithoutComments(source: String): String {
    val code = StringBuilder(source.length)
    var index = 0
    while (index < source.length) {
        val comment = commentEnd(source, index)
        val literal = if (comment == null) literalEnd(source, index) else null
        when {
            comment != null -> code.append(' ')
            literal != null -> code.append(source, index, literal)
            else -> code.append(source[index])
        }
        index = comment ?: literal ?: (index + 1)
    }
    return code.toString()
}

/**
 * The index just past the comment starting at [start], or null if none does.
 */
private fun commentEnd(source: String, start: Int): Int? = when {
    source.startsWith("//", start) -> lineCommentEnd(source, start)
    source.startsWith("/*", start) -> blockCommentEnd(source, start)
    else -> null
}

private fun lineCommentEnd(source: String, start: Int): Int {
    val newline = source.indexOf('\n', start)
    return if (newline < 0) source.length else newline
}

/**
 * Kotlin block comments nest, so `/*` `/*` `*/` `*/` closes once, not twice.
 * An unterminated comment runs to the end of the file.
 */
private fun blockCommentEnd(source: String, start: Int): Int {
    var depth = 0
    var index = start
    do {
        when {
            source.startsWith("/*", index) -> {
                depth += 1
                index += 2
            }

            source.startsWith("*/", index) -> {
                depth -= 1
                index += 2
            }

            else -> index += 1
        }
    } while (depth > 0 && index < source.length)
    return index
}

/**
 * The index just past the string or character literal starting at [start], or
 * null if none does.
 */
private fun literalEnd(source: String, start: Int): Int? = when {
    source.startsWith("\"\"\"", start) -> rawStringEnd(source, start)
    source[start] == '"' || source[start] == '\'' -> quotedEnd(source, start, source[start])
    else -> null
}

private fun rawStringEnd(source: String, start: Int): Int {
    val close = source.indexOf("\"\"\"", start + 3)
    return if (close < 0) source.length else close + 3
}

private fun quotedEnd(source: String, start: Int, quote: Char): Int {
    var index = start + 1
    while (index < source.length) {
        if (source[index] == '\\') {
            index += 2
            continue
        }
        if (source[index] == quote) {
            return index + 1
        }
        index += 1
    }
    return source.length
}
