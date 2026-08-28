package tallyvane.gradle.migrationpolicy

import org.gradle.api.Plugin
import org.gradle.api.Project
import tallyvane.gradle.migrationpolicy.tasks.CheckAdditiveMigrationsTask

/**
 * Composition root of `tallyvane.migration-policy` (ADR-066).
 *
 * Registers `checkAdditiveMigrations`, pointed at the git repository root (one level above the
 * `backend` build this plugin is applied to). [CheckAdditiveMigrationsTask.baseRef]'s only
 * convention here is `MIGRATION_POLICY_BASE_SHA`, read as a plain environment variable rather
 * than resolved by running `git` — an external process at configuration time is a
 * configuration-cache violation (measured: "external process started 'git merge-base …'... is
 * unsupported" the first time this was tried), which is also why the task itself, not this
 * plugin, computes the local-run fallback at execution time. Holds no policy logic: that lives
 * in [tallyvane.gradle.migrationpolicy.domain.MigrationPolicy].
 *
 * Apply with `id("tallyvane.migration-policy")` or via `tallyvane.root`. Do not instantiate
 * this class from a build script.
 */
class MigrationPolicyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("checkAdditiveMigrations", CheckAdditiveMigrationsTask::class.java) { task ->
            task.repositoryRoot.convention(target.layout.projectDirectory.dir(".."))
            task.baseRef.convention(target.providers.environmentVariable("MIGRATION_POLICY_BASE_SHA"))
        }
    }
}
