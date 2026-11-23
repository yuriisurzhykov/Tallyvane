package tallyvane.knowledge.infra

import java.nio.file.Files
import java.nio.file.Path

/**
 * The settings the application runs with, read from the environment. A setting that is needed and missing stops
 * the start with the name of the variable, and a number that is not a number does the same, so a mistake in
 * `.env` is found at once and not at the first answer.
 */
class Environment(private val values: Map<String, String>) {
    fun text(name: String, default: String): String = values[name]?.takeIf { it.isNotBlank() } ?: default

    fun required(name: String): String =
        values[name]?.takeIf { it.isNotBlank() }
            ?: throw StartupFailure("$name is not set. Copy .env.example to .env and fill it in.")

    fun whole(name: String, default: Int): Int =
        values[name]?.takeIf { it.isNotBlank() }?.let {
            it.trim().toIntOrNull() ?: throw StartupFailure("$name must be a whole number, not \"$it\".")
        } ?: default

    fun decimal(name: String, default: Double): Double =
        values[name]?.takeIf { it.isNotBlank() }?.let {
            it.trim().toDoubleOrNull() ?: throw StartupFailure("$name must be a number, not \"$it\".")
        } ?: default

    companion object {
        /** The process environment, with the lines of a `.env` file in the working directory under it. */
        fun ofProcess(dotenv: Path = Path.of(".env")): Environment = Environment(fromFile(dotenv) + System.getenv())

        internal fun fromFile(file: Path): Map<String, String> {
            if (!Files.isRegularFile(file)) return emptyMap()
            return Files.readAllLines(file)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .associate { line ->
                    val name = line.substringBefore('=').trim().removePrefix("export ").trim()
                    name to line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                }
        }
    }
}
