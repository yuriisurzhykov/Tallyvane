# graph — `tallyvane.graph`

`modules.yaml` is the contract for who may depend on whom. This plugin
compares that file to the real Gradle project graph. An undeclared edge and
a declared but unused one are both errors. MockK and Mockito coordinates are
banned on every configuration, including test.

The plugin class sits at the package root:
`tallyvane.gradle.graph.ModuleGraphPlugin`. Gradle Task subclasses live under
`tasks/`. Everything else is cut by the same layers as a feature module,
as packages inside one `:graph` project — not as extra Gradle modules, and
not as folders named after a technology.

```
ModuleGraphPlugin.kt          composition root: register, snapshot, get out
domain/
  Finding.kt                  a check result (toString is the Gradle line)
  Feature.kt                  values plus the paths they imply
  Platform.kt                 values plus the paths they imply
  GraphCheck.kt               Planned, Unlisted, Platforms, Features, Banned
  ModulesYaml.kt              port — no SnakeYAML
  IncludedProjects.kt         port + Snapshot + Wired (configuration cache)
application/
  ValidateModuleGraph.kt      use case: run the checks, return findings
infrastructure/
  YamlModulesYaml.kt          the only class that imports SnakeYAML
tasks/
  ValidateModuleGraphTask.kt  @TaskAction: use case, then GradleException
```

A first pass put every type in one package, nested `File` on `ModulesYaml`,
and treated `Feature` as a port with `FromManifest`. That matched an Android
plugin's vocabulary-in-one-file shape and kept `settings.gradle.kts` to one
project per plugin. It also put SnakeYAML on the port, put Gradle next to
the comparison rules, and invented an interface for a value nobody
substitutes. `manifest/` / `rules/` / `gradle/` were the other wrong cut:
those names describe a technology, not a layer, so YAML knowledge and
"what may depend on what" still sat in the wrong files. `:graph:domain` as
its own Gradle project would compile-isolate the domain from Gradle, but
it would also rewrite ARCHITECTURE.md's "one Gradle project per plugin".
Packages inside `:graph` keep that rule. Isolation is by convention:
`GraphCheck.kt` does not import Gradle or SnakeYAML; `IncludedProjects.kt`
is the one domain file that does, because part two of the engineering
principles puts `Snapshot` and `Wired` on the same port.

`YamlModulesYaml` reads the file. `Feature` and `Platform` are data classes
the adapter constructs — formatting `:modules:jobs:contract` is not a
reason to allocate a throwaway object, and it is not a reason to extract a
port. `ValidateModuleGraph` holds the yaml and the projects and returns
findings. The task exists because configuration cache needs serialisable
`@Input` values: `accept` writes them, `Wired` rebuilds the same port at
execution, and `@TaskAction` translates a non-empty list into a
`GradleException`.

Test doubles are `ModulesYamlFake` and `IncludedProjectsFake` in `src/test`,
not nested on the production types (ADR-044).

## Test edges count too

`Snapshot` reads `test*`, `integrationTest*` and `testFixtures*` configurations
alongside `api`, `implementation` and `compileOnly`. §15.2 asks this task to
resolve the real Gradle graph, and a module reached only from `src/test` is still
one module reaching another; a test-only edge between two capabilities is exactly
the coupling the manifest exists to make visible in a diff.

There is no second manifest key for test permissions. If a test needs an edge its
module may not declare, the test is in the wrong layer: an integration test of a
use case belongs to `infrastructure`, where `platform:*` is already allowed, not
to `application`, where it is not.

Widening the list found its first case immediately, and it is one compile
configurations never produce: a module that consumes its own `testFixtures`
registers a project dependency on itself, which reported `:platform:kernel` as
depending on `:platform:kernel`. Self-references are dropped — using one's own
fixtures reaches nobody. `IncludedProjectsSnapshotSpec` pins all of it, including
a control that `runtimeOnly` is still ignored, so the four positive cases cannot
pass by the filter having been removed rather than widened.

Runtime configurations stay out: they grant no compile-time access.

## 2026-08-25 — the capability nobody wrote down

Four checks read the manifest and compared it to Gradle, and between them they
left one path uncovered: `Planned` reports a name only when it **is** under
`planned:`, and `Features` iterates what `modules:` declares. A project
`:modules:<name>:<layer>` whose capability appears in neither list was therefore
invisible to both, along with every edge it drew — the manifest quietly stopped
being authoritative for exactly the module nobody had written down.

`Unlisted` closes it by starting from Gradle rather than from the manifest, which
is why it is a separate type instead of a branch inside `Planned`: the two ask
opposite questions of the same paths. It reports once per capability, not per
layer, since five layers of one undeclared module are one thing to fix.

Found by review while `modules:` was still empty, so nothing could exercise the
hole yet — the first capability would have made it live. `GraphCheckUnlistedSpec`
includes the case that pins *why* the check exists: given a manifest that is
internally consistent, `Planned` and `Features` both report nothing about an
undeclared module that depends on `platform:http`. Writing that case caught a
mistake in a neighbouring one, where the fake declared a capability the fake build
did not contain, so `Features` failed for an unrelated reason.

Applying `id("tallyvane.graph")` registers `validateModuleGraph`.
`tallyvane.root` applies this plugin and `tallyvane.verification`.

## SOLID

Single responsibility is the layer cut and "one class per outside world":
SnakeYAML has one owner, the Gradle `Project` graph has one owner, the task
does not expand allow-list tokens. Open/closed is a new nested type on
`GraphCheck`; the use case's default list is the composition, not a `when`
inside a check. Liskov is why `Feature` is not nested on `ModulesYaml` and
why `YamlModulesYaml` implements the whole port rather than a parser that
callers special-case. Interface segregation is why `ModulesYaml` and
`IncludedProjects` are two ports — a check that only reads coordinates does
not grow a YAML method. Dependency inversion is the plugin as composition
root: `GraphCheck` depends on the ports, `YamlModulesYaml` and `Snapshot`
are named only at the edge.
