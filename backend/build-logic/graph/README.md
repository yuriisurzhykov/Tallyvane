# graph — `tallyvane.graph`

`modules.yaml` is the contract for who may depend on whom. This plugin
compares that file to the real Gradle project graph. An undeclared edge and
a declared but unused one are both errors. MockK and Mockito coordinates are
banned on every configuration, including test.

The plugin class sits at the package root:
`tallyvane.gradle.graph.ModuleGraphPlugin`. Vocabulary is nested types on
ports, in the same package. Gradle Task subclasses live under `tasks/`.

```
ModuleGraphPlugin.kt     Plugin<Project> — register the task, accept a snapshot
ModulesYaml.kt           interface + File (SnakeYAML stays inside File)
Feature.kt               port + FromManifest
Platform.kt              port + FromManifest
IncludedProjects.kt      interface + Snapshot (live Gradle) + Wired (@Input)
GraphCheck.kt            interface + Planned, Platforms, Features, Banned
GraphCheckRunner.kt      interface + Base(yaml, projects)
Finding.kt               a check result
tasks/
  ValidateModuleGraphTask.kt
```

`ModulesYaml.File` reads the YAML. `Feature` and `Platform` are their own
ports (`FromManifest` on each), not nested types on the yaml file: a
`Feature` is not a kind of `ModulesYaml`, and formatting a Gradle path is
not a reason to construct one. `IncludedProjects.Snapshot` walks the
Gradle build. `GraphCheckRunner.Base` holds both and runs the nested checks.
The task exists because configuration cache needs serializable `@Input`
values: `accept` writes them, `Wired` rebuilds the same port at execution.

Test doubles are `ModulesYamlFake` and `IncludedProjectsFake` in `src/test`,
not nested on the production types (ADR-044).

Applying `id("tallyvane.graph")` registers `validateModuleGraph`.
`tallyvane.root` applies this plugin and `tallyvane.verification`.
