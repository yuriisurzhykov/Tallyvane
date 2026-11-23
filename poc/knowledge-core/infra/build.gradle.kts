plugins {
    application
    `jvm-test-suite`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.application)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation(libs.postgresql)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(testFixtures(projects.application))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("tallyvane.knowledge.infra.MainKt")
}

// `./gradlew run` starts from the root of the build, where `.env.example` and the compose file are, so that
// the editable prompts are found at their path in the source tree without a rebuild.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

// The integration tests need a real Postgres with pgvector, so they are a suite of their own and `build`
// does not run them (ADR-057): `./gradlew :infra:integrationTest`.
testing.suites.register<JvmTestSuite>("integrationTest") {
    useJUnitJupiter()
    dependencies {
        implementation(project())
        implementation(testFixtures(projects.application))
        implementation(libs.kotest.runner.junit5)
        implementation(libs.kotest.assertions.core)
        implementation(libs.testcontainers.postgresql)
        implementation(libs.kotlinx.coroutines.core)
    }
    targets.configureEach {
        testTask.configure {
            useJUnitPlatform()
            // A database named here is used in place of a container, for machines without Docker.
            listOf("POC_TEST_DATABASE_URL", "POC_TEST_DATABASE_USER", "POC_TEST_DATABASE_PASSWORD").forEach { name ->
                System.getenv(name)?.let { environment(name, it) }
            }
        }
    }
}
