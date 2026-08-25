package tallyvane.gradle.graph.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

private fun leaf(name: String, parent: Project): Project =
    ProjectBuilder
        .builder()
        .withName(name)
        .withParent(parent)
        .build()
        .also { it.plugins.apply("java-library") }

class IncludedProjectsSnapshotSpec :
    StringSpec({
        "sees an edge declared only from tests, because a test edge is access too" {
            val root = ProjectBuilder.builder().build()
            val consumer = leaf("consumer", root)
            val producer = leaf("producer", root)
            consumer.dependencies.add("testImplementation", producer)

            IncludedProjects.Snapshot(root).dependencies(":consumer") shouldContain ":producer"
        }

        "sees an edge declared from an integration test suite" {
            val root = ProjectBuilder.builder().build()
            val consumer = leaf("consumer", root)
            val producer = leaf("producer", root)
            consumer.configurations.create("integrationTestImplementation")
            consumer.dependencies.add("integrationTestImplementation", producer)

            IncludedProjects.Snapshot(root).dependencies(":consumer") shouldContain ":producer"
        }

        "sees an edge declared from test fixtures, which other modules consume" {
            val root = ProjectBuilder.builder().build()
            val consumer = leaf("consumer", root)
            val producer = leaf("producer", root)
            consumer.plugins.apply("java-test-fixtures")
            consumer.dependencies.add("testFixturesImplementation", producer)

            IncludedProjects.Snapshot(root).dependencies(":consumer") shouldContain ":producer"
        }

        "ignores a module consuming its own test fixtures, which reaches nobody" {
            val root = ProjectBuilder.builder().build()
            val consumer = leaf("consumer", root)
            consumer.plugins.apply("java-test-fixtures")

            IncludedProjects.Snapshot(root).dependencies(":consumer") shouldNotContain ":consumer"
        }

        "ignores a runtime-only edge, which grants no compile-time access" {
            val root = ProjectBuilder.builder().build()
            val consumer = leaf("consumer", root)
            val producer = leaf("producer", root)
            consumer.dependencies.add("runtimeOnly", producer)

            IncludedProjects.Snapshot(root).dependencies(":consumer") shouldNotContain ":producer"
        }
    })
