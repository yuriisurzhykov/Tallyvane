# graph — `tallyvane.graph`

`modules.yaml` is the contract for who may depend on whom. This plugin
compares that file to the real Gradle project graph. An undeclared edge and
a declared but unused one are both errors. MockK and Mockito coordinates are
banned on every configuration, including test.

The plugin class sits at the module package root:
`tallyvane.gradle.graph.ModuleGraphPlugin`. Everything else is one of three
jobs, as packages in this same Gradle project — not as subprojects.

```
ModuleGraphPlugin.kt   Plugin<Project>
manifest/              modules.yaml → ModuleManifest (SnakeYAML stays here)
rules/                 ModuleManifest + ProjectGraph → GraphFinding
gradle/                Task, snapshot of the Gradle build, configuration-cache wiring
```

`rules` does not import YAML or `org.gradle.api.Project`. The task loads the
manifest, accepts a `ProjectGraph` snapshot, and calls `ValidateModuleGraph`.
The four checks (planned modules, platform edges, feature edges, banned
coordinates) are separate types; they are not a registry, because the set is
fixed.

Applying `id("tallyvane.graph")` registers `validateModuleGraph`.
`tallyvane.root` applies this plugin and `tallyvane.verification`.
