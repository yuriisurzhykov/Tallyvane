package tallyvane.gradle.migrationpolicy.infrastructure

import tallyvane.gradle.migrationpolicy.domain.ChangedMigrationFiles
import tallyvane.gradle.migrationpolicy.domain.MigrationFile
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * [ChangedMigrationFiles] answered by shelling out to the `git` already on every machine that
 * can run this build — CI and every contributor's own clone — rather than a JVM git library
 * this module would otherwise need to depend on for one command.
 *
 * Reads content from the working tree at call time, not `git show HEAD:<path>`: a local
 * `./gradlew check` should see uncommitted edits, the same way every other check here does.
 *
 * @param repositoryRoot The git repository's own root — one level above the `backend` Gradle
 * build this plugin is applied to. `git diff --name-only` reports paths relative to this
 * directory regardless of where the process is started from within the tree, which is why every
 * returned [MigrationFile.path] is resolved against it, not against the caller's own working
 * directory.
 */
internal class GitChangedMigrationFiles(
    private val repositoryRoot: File,
) : ChangedMigrationFiles {
    override fun changedSince(baseRef: String): List<MigrationFile> =
        changedPaths(baseRef)
            .filter(::isMigrationFile)
            .map { path -> MigrationFile(path, File(repositoryRoot, path).readText()) }

    /**
     * @param baseRef Passed straight through to `git diff` as one ref among two; `git` itself
     * rejects anything that is not a real ref, which is the validation this needs.
     * @return Repository-relative paths of files `git diff --diff-filter=AM` reports as added
     * or modified between [baseRef] and HEAD.
     */
    private fun changedPaths(baseRef: String): List<String> {
        val process =
            ProcessBuilder("git", "diff", "--name-only", "--diff-filter=AM", baseRef, "HEAD")
                .directory(repositoryRoot)
                .redirectErrorStream(false)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(finished) { "git diff against $baseRef did not finish within ${GIT_TIMEOUT_SECONDS}s" }
        check(process.exitValue() == 0) { "git diff against $baseRef failed: $error" }
        return output.lineSequence().filter { line -> line.isNotBlank() }.toList()
    }

    private fun isMigrationFile(path: String): Boolean = MIGRATION_PATH.containsMatchIn(path)

    private companion object {
        const val GIT_TIMEOUT_SECONDS = 30L

        val MIGRATION_PATH = Regex("""/db/migration/.*\.sql$""")
    }
}
