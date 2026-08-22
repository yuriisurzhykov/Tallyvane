# build-logic

An included build with **one Gradle project per plugin**, plus `:ktlint-rules`
for style official ktlint does not cover.

```
conventions/     tallyvane.kotlin-module and siblings (precompiled scripts)
graph/           tallyvane.graph — domain / application / infrastructure packages, Task in tasks/
verification/    tallyvane.verification
root/            tallyvane.root — applies graph + verification
ktlint-rules/    tallyvane ktlint ruleset — style official ktlint does not cover
```

A plugin class lives at the root of that plugin's package
(`tallyvane.gradle.graph.ModuleGraphPlugin`, not under `...graph.plugin`).

Convention scripts apply other people's plugins and set the toolchain. A
module's own build file stays three lines because of those:

```kotlin
plugins { id("tallyvane.kotlin-module") }

dependencies {
    api(projects.platform.kernel)
    implementation(projects.modules.jobs.contract)
}
```

Binary plugins are for rules this repository owns. Graph internals:
[graph/README.md](graph/README.md). ktlint ruleset:
[ktlint-rules/README.md](ktlint-rules/README.md). `tallyvane.kotlin-module`
puts that JAR on `ktlintRuleset`.

## Why an included build rather than a root `subprojects` block

Configuration injected from the root build file is invisible from the module
you are reading. You open `modules/jobs/domain/build.gradle.kts`, see three
lines, and have no way to tell what else is being applied. A convention plugin
does the same job while staying findable: the module names the plugin, and the
plugin is a file you can open.

## Plugins

| Plugin                     | Project           | Kind       | Responsibility                                                                 |
| -------------------------- | ----------------- | ---------- | ------------------------------------------------------------------------------ |
| `tallyvane.kotlin-module`  | `:conventions`    | convention | Kotlin JVM, toolchain 21, `allWarningsAsErrors`, Kotest, ktlint, detekt        |
| `tallyvane.pure-module`    | `:conventions`    | convention | The above, plus `explicitApi()`                                                |
| `tallyvane.adapter-module` | `:conventions`    | convention | Same rails; Testcontainers come with the first adapter                         |
| `tallyvane.web-module`     | `:conventions`    | convention | Same rails; Ktor test host comes with the first route                          |
| `tallyvane.graph`          | `:graph`          | binary     | `validateModuleGraph`: `modules.yaml` vs the Gradle graph, banned MockK/Mockito |
| `tallyvane.verification`   | `:verification`   | binary     | `arch` / `check` aggregator                                                    |
| `tallyvane.root`           | `:root`           | binary     | Applies graph + verification                                                   |

`./gradlew arch` is ktlint + detekt on every leaf, the graph check, this
included build's own `check`, and `:arch-tests:test`. Empty parent projects
(`:platform`) are skipped on the backend side.

ktlint is not applied inside this included build. `kotlin-dsl` (on
`:conventions`) puts generated accessors on the main source set; ktlint then
lints Gradle's files. Detekt on the Kotlin JVM leaves is pointed at `src/`
only.
