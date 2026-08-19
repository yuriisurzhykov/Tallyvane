# build-logic

Convention plugins. This is what keeps a module's own build file down to three
lines:

```kotlin
plugins { id("tallyvane.kotlin-module") }

dependencies {
    api(projects.platform.kernel)
    implementation(projects.modules.jobs.contract)
}
```

## Why an included build rather than a root `subprojects` block

Configuration injected from the root build file is invisible from the module
you are reading. You open `modules/jobs/domain/build.gradle.kts`, see three
lines, and have no way to tell what else is being applied. A convention plugin
does the same job while staying findable: the module names the plugin, and the
plugin is a file you can open.

## Planned plugins

| Plugin                     | Applies to           | Responsibility                                                        |
| -------------------------- | -------------------- | --------------------------------------------------------------------- |
| `tallyvane.kotlin-module`  | every module         | Kotlin JVM, toolchain, compiler flags, common test wiring             |
| `tallyvane.pure-module`    | `domain`, `contract` | The above, minus anything that could pull in a framework              |
| `tallyvane.adapter-module` | `infrastructure`     | The above, plus integration-test source set and Testcontainers wiring |
| `tallyvane.web-module`     | `web`                | The above, plus serialization and the Ktor test host                  |

Splitting them is not ceremony. `pure-module` exists so that a `domain` module
physically cannot acquire a framework dependency by copy-paste — the plugin it
uses does not put one on the classpath in the first place.

## Wiring

Once this directory has content, uncomment the `pluginManagement` block in
`../settings.gradle.kts`. Until then it stays out of the build so the skeleton
carries no dependencies at all.
