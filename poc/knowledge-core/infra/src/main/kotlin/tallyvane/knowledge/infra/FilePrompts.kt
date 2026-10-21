package tallyvane.knowledge.infra

import tallyvane.knowledge.domain.port.Prompts
import java.nio.file.Files
import java.nio.file.Path

/**
 * The prompt of a step, from `<step>.md`. A directory is read first, on every call, so that a prompt edited
 * while the application runs is used by the next answer without rebuilding anything; the copy inside the
 * application is the fallback when there is no directory or no such file in it.
 */
class FilePrompts(private val directory: Path?) : Prompts {
    override fun prefix(step: String): String {
        directory?.resolve("$step.md")?.takeIf { Files.isRegularFile(it) }?.let { return Files.readString(it).trimEnd() }
        val resource =
            FilePrompts::class.java.getResourceAsStream("/prompts/$step.md")
                ?: throw StartupFailure("There is no prompt for the $step step.")
        return resource.use { String(it.readBytes(), Charsets.UTF_8).trimEnd() }
    }
}
