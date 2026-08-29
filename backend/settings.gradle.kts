rootProject.name = "tallyvane"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("build-logic")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// ---------------------------------------------------------------------------
// Platform — technical capabilities. Zero business logic, zero knowledge of
// any feature module. Nothing here may ever depend on modules/*.
// ---------------------------------------------------------------------------
include(":platform:kernel")
include(":platform:events")
include(":platform:persistence")
include(":platform:http")
include(":platform:outbox")
include(":platform:llm")
include(":platform:storage")
include(":platform:exec")
include(":platform:observability")
include(":platform:health")

// ---------------------------------------------------------------------------
// Composition root and architecture tests.
// ---------------------------------------------------------------------------
include(":server")
include(":arch-tests")

// The one-shot migration command ADR-051 calls for: run by the deploy, never by the
// application at startup. Outside modules.yaml for the same reason `server` and
// `arch-tests` are — it is not a platform capability and not a feature.
include(":migrate")

// ---------------------------------------------------------------------------
// Spikes — code written to answer one question, kept so the answer stays
// checkable. Outside the Konsist scopes and outside modules.yaml on purpose;
// `check` compiles them and nothing else. See playground/README.md.
// ---------------------------------------------------------------------------
include(":playground:transactions")
include(":playground:isolation")
include(":playground:ddl-locks")
include(":playground:timeout-bounds")
include(":playground:pool-occupancy")
include(":playground:health")
include(":playground:http")

// ---------------------------------------------------------------------------
// Feature modules — one include per layer, added as each capability is built.
// The authoritative dependency manifest is modules.yaml; the shape to copy is
// modules/_template, which is deliberately NOT included here so Gradle ignores
// it.
//
// Milestone 1 will add, in this order:
//   include(":modules:identity:contract")
//   include(":modules:identity:domain")
//   include(":modules:identity:application")
//   include(":modules:identity:infrastructure")
//   include(":modules:identity:web")
// ---------------------------------------------------------------------------
