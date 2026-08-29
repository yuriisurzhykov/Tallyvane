package tallyvane.gradle.migrationpolicy.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import tallyvane.gradle.migrationpolicy.domain.MigrationFile
import java.io.File
import java.nio.file.Files

/**
 * Against a real, throwaway git repository — not a fake — because this class's only job is to
 * get `git diff`'s own paths and filtering right, and a fake of `git` would only prove this
 * class agrees with itself about what `git` does.
 */
class GitChangedMigrationFilesSpec :
    StringSpec({
        "reports an added migration file, and nothing that is not one" {
            withRepo { repo ->
                commit(repo, "V1__baseline.sql" to "create table platform.x (id uuid);")
                val base = currentCommit(repo)

                write(
                    repo,
                    "backend/platform/persistence/src/main/resources/db/migration/platform/V2__add_y.sql",
                    "alter table platform.x add column y text;",
                )
                write(repo, "README.md", "not a migration")
                commit(repo)

                GitChangedMigrationFiles(repo).changedSince(base).map(MigrationFile::path) shouldContainExactly
                    listOf("backend/platform/persistence/src/main/resources/db/migration/platform/V2__add_y.sql")
            }
        }

        "reads the file's content at HEAD" {
            withRepo { repo ->
                val base = currentCommit(repo)
                write(
                    repo,
                    "backend/platform/persistence/src/main/resources/db/migration/platform/V1__x.sql",
                    "drop table platform.x;",
                )
                commit(repo)

                val files = GitChangedMigrationFiles(repo).changedSince(base)

                files.single().content shouldBe "drop table platform.x;"
            }
        }

        "no changes since the given ref is an empty list, not an error" {
            withRepo { repo ->
                commit(repo, "V1__baseline.sql" to "create table platform.x (id uuid);")
                val head = currentCommit(repo)

                GitChangedMigrationFiles(repo).changedSince(head).shouldBeEmpty()
            }
        }
    })

private fun withRepo(block: (File) -> Unit) {
    val repo = Files.createTempDirectory("migration-policy-spec").toFile()
    try {
        git(repo, "init", "--initial-branch=master")
        git(repo, "config", "user.email", "spec@example.com")
        git(repo, "config", "user.name", "spec")
        commit(repo)
        block(repo)
    } finally {
        repo.deleteRecursively()
    }
}

private fun commit(repo: File, vararg migrations: Pair<String, String>) {
    for ((name, content) in migrations) {
        write(repo, "backend/platform/persistence/src/main/resources/db/migration/platform/$name", content)
    }
    commit(repo)
}

private fun commit(repo: File) {
    git(repo, "add", "-A")
    git(repo, "commit", "--allow-empty", "-m", "commit")
}

private fun write(repo: File, path: String, content: String) {
    val file = File(repo, path)
    file.parentFile.mkdirs()
    file.writeText(content)
}

private fun currentCommit(repo: File): String = git(repo, "rev-parse", "HEAD").trim()

private fun git(repo: File, vararg args: String): String {
    val process = ProcessBuilder(listOf("git") + args).directory(repo).start()
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $error" }
    return output
}
