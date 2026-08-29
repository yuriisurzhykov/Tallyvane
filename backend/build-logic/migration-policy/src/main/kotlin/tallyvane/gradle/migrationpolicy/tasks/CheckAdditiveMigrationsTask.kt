package tallyvane.gradle.migrationpolicy.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import tallyvane.gradle.migrationpolicy.domain.MigrationPolicy
import tallyvane.gradle.migrationpolicy.infrastructure.GitChangedMigrationFiles
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Gradle adapter for [MigrationPolicy].
 *
 * Declares no outputs on purpose: the real input is `git diff` against [baseRef], which Gradle
 * has no native way to hash, so this task is never up-to-date and always runs — the same
 * position `tallyvane.graph`'s `validateModuleGraph` already takes for a different reason
 * (a live `Project` cannot be an `@Input`).
 *
 * Group is `verification`. [ignoreFailures] stays `false`: a destructive migration in a
 * blue-green release is a failed build, not a warning (ADR-066).
 */
abstract class CheckAdditiveMigrationsTask :
    DefaultTask(),
    VerificationTask {
    /**
     * The commit `git diff` compares HEAD against — a pull request's base SHA, or a push
     * event's previous tip, supplied by CI as `MIGRATION_POLICY_BASE_SHA`.
     *
     * Optional: a local run with nothing configured falls back, at execution time, to the
     * merge-base with `origin/master` — computed here rather than as a configuration-time
     * `Provider` default, because starting `git` while Gradle is still configuring the build is
     * a configuration-cache violation ([tallyvane.gradle.migrationpolicy.MigrationPolicyPlugin]'s
     * own KDoc has the measurement).
     */
    @get:Input
    @get:Optional
    abstract val baseRef: Property<String>

    /**
     * The git repository's own root, one level above the `backend` Gradle build.
     *
     * `@Internal`, not `@InputDirectory`: this task's real input is git history, which nothing
     * here asks Gradle to hash — hashing every file in the repository to decide whether this
     * task is up to date would be both wrong (git history matters, file content at HEAD is not
     * the whole story) and enormously slow.
     */
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    init {
        group = "verification"
        description = "Fails if a migration added or changed relative to baseRef contains a destructive statement."
        ignoreFailures = false
    }

    @TaskAction
    fun validate() {
        val root = repositoryRoot.get().asFile
        val resolvedBaseRef = if (baseRef.isPresent) baseRef.get() else localFallbackBaseRef(root)
        val changed = GitChangedMigrationFiles(root).changedSince(resolvedBaseRef)
        val findings = MigrationPolicy().findings(changed)
        if (findings.isEmpty()) {
            logger.info(
                "Checked {} migration file(s) added or changed relative to {} — all additive.",
                changed.size,
                resolvedBaseRef,
            )
            return
        }
        throw GradleException(
            findings.joinToString(
                separator = "\n",
                prefix = "Destructive migration(s) added or changed relative to $resolvedBaseRef (ADR-066):\n",
            ),
        )
    }

    /**
     * @return The merge-base of HEAD and `origin/master`, or `HEAD` itself — which makes the
     * later `git diff` compare a ref against itself and report no changes — if that ref does
     * not exist or the command fails for any reason. The fallback is logged, not silent: a
     * check that quietly answers "nothing changed" for the wrong reason is the one failure mode
     * this repository's own rules single out as worse than a check that fails loudly.
     */
    private fun localFallbackBaseRef(root: File): String {
        val mergeBase = runCatching { mergeBase(root) }.getOrNull()
        if (mergeBase != null) {
            return mergeBase
        }
        logger.warn(
            "checkAdditiveMigrations: could not compute a merge-base with origin/master (no such ref, or git " +
                "failed) — comparing HEAD against itself, which reports no changes. Set MIGRATION_POLICY_BASE_SHA " +
                "to check against a real base.",
        )
        return "HEAD"
    }

    private fun mergeBase(root: File): String? {
        val process =
            ProcessBuilder("git", "merge-base", "HEAD", "origin/master")
                .directory(root)
                .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val finished = process.waitFor(MERGE_BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return if (finished && process.exitValue() == 0 && output.isNotEmpty()) output else null
    }

    private companion object {
        const val MERGE_BASE_TIMEOUT_SECONDS = 10L
    }
}
