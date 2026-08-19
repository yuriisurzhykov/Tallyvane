# Tallyvane — backend

A modular monolith in Kotlin. One process, one database, one deployment — with
boundaries between capabilities as hard as they would be between services.

Full reasoning in [ARCHITECTURE.md](../ARCHITECTURE.md) sections 4 through 6.
This file covers only what you need to move around the tree.

## Two axes of decomposition

The system is cut twice. **Vertically** into capabilities — `jobs`,
`applications`, `contacts`, `resume` and so on. **Horizontally** into layers
inside each capability — `contract`, `domain`, `application`, `infrastructure`,
`web`. The intersection of the two axes is a Gradle module, and that is why the
compiler can enforce the boundary rather than a reviewer.

```
backend/
├── build-logic/        convention plugins; every module's build file is 3 lines
├── platform/           technical capabilities, zero business logic
├── modules/            capabilities (none yet; _template is the shape to copy)
├── app/                composition root — the only place implementations are named
├── arch-tests/         Konsist rules
└── modules.yaml        who may depend on whom; checked against the real graph
```

## The rules, in short

A module publishes exactly one thing to its neighbours: its `contract` module.
Everything else is invisible, because the dependency is not in the graph at all.

`domain` knows nothing but `platform:kernel` — not even its own `application`
layer. `application` may read other capabilities through their contracts.
`infrastructure` and `web` are adapters and are never depended upon.

Reads between capabilities are synchronous through a contract when the caller
needs the answer to continue. Reactions go through events, with no dependency
in either direction.

Each module owns a PostgreSQL schema named after it. Foreign keys across
schemas are allowed; joins across them are not. To learn something about
another capability's data you ask its module, you do not reach into its tables.

## Adding a capability

1. Copy `modules/_template` to `modules/<name>` and rename the packages.
2. Delete the layers the capability does not need. A missing layer is a normal
   state, not an omission — `analytics` has no contract and no domain because
   nothing reads from it and it owns no rules of its own.
3. Move its entry in `modules.yaml` from `planned` to `modules`.
4. Add one `include` line per layer in `settings.gradle.kts`.
5. Register its `RouteModule` and wiring in `app`.

No existing module is opened at any step. If one has to be, the boundary is in
the wrong place.

## Running it

```
./gradlew projects        # the module graph
./gradlew tasks           # what is available
```

Nothing needs installing. Gradle 9.7.0 comes down on first use and lives in the
Gradle user home, not in this repository. JDK 21 is the only prerequisite.

### The distribution checksum is pinned

`gradle/wrapper/gradle-wrapper.properties` carries `distributionSha256Sum`.
The wrapper refuses to run if the downloaded archive does not match, so a
compromised mirror or a corrupted transfer fails loudly instead of executing
whatever arrived.

Both the distribution and `gradle-wrapper.jar` were verified against Gradle's
published checksums when this wrapper was generated. Do not regenerate the
wrapper without repeating that check — the jar is executable code committed to
the repository, and it is the one file here nobody can read to review.

When upgrading:

```
./gradlew wrapper --gradle-version <new> --gradle-distribution-sha256-sum <sum from services.gradle.org>
```

## Deliberately absent

No dependencies are declared anywhere yet — no Ktor, no Exposed, no test
framework. The skeleton exists first so that the rules are in place before the
first line of logic, rather than being retrofitted against code that already
disagrees with them.
