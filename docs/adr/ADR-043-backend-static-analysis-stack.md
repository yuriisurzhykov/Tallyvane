# ADR-043. Backend static analysis: ktlint, detekt, Konsist, module graph

## Decision

Four tools, four questions, no overlap:

- **Gradle module graph vs `modules.yaml`** — who may depend on whom. An undeclared
  edge and a declared but unused one are both errors. MockK and Mockito
  coordinates are banned on every configuration, including test.
- **ktlint** — official Kotlin style with trailing commas, on the `intellij_idea`
  profile. The commas are not the profile's doing: `backend/.editorconfig` sets
  `ij_kotlin_allow_trailing_comma` and both `ktlint_standard_trailing-comma-*`
  rules by hand, and either profile enforces them. Why not `ktlint_official`,
  which this record chose first, is below. Plugin
  `org.jlleitschuh.gradle.ktlint`. Not
  `detekt-formatting`: that is a second ktlint, two reports for one edit.
  Style official ktlint does not cover lives in `build-logic/ktlint-rules`
  (`tallyvane` ruleset), loaded on the same `ktlintCheck` task. First rule:
  every KDoc is the multiline form — `kdoc-wrapping` only forbids sharing a
  line with other code.
- **detekt** — size and complexity as SOLID proxies, with Kotlin-native
  thresholds, not the frontend ESLint numbers. Function parameters and
  constructor parameters are different knobs: a use case constructor holding
  ports is DIP, not a fat function API.
- **Konsist** — architecture and naming from ARCHITECTURE.md §15.3. It does
  not re-check cyclomatic complexity.

Kotest remains the test runner. Handwritten fakes in `src/test` (`JobsFake`)
are the only test doubles — placement is ADR-044; this record's nested
`Jobs.Fake` is superseded. MockK is not a stubbing escape hatch: a `mockk()`
double does not implement the port and will not fail the conformance suite when
a method is added.

Smart contract: I/O-free *production* implementations nest inside the port
(`Cached`, `Retrying`; `Abstract` only as a Template Method). A `Fake` does
not nest: it would compile into the production jar. Technology adapters live
in `*:infrastructure`, `internal`, named by mechanism (`PostgresJobs`). They
cannot be `Jobs.Postgres` — a nested type is compiled in the interface's
module.

## Thresholds (detekt 2)

- LongMethod: 60 lines
- CyclomaticComplexMethod: 12
- NestedBlockDepth: 3
- LongParameterList: 3 function parameters, 5 constructor parameters
- LargeClass: 400 lines
- TooManyFunctions: detekt default (11)

Test source sets: LongMethod and LargeClass off. TooManyFunctions already
excludes tests. Cyclomatic complexity, depth and parameter lists stay on.

## Rejected alternatives

**Everything in Konsist.** Formatting and cyclomatic complexity are not
Konsist's job, and a home-grown formatter would drift from official style.

**Everything in detekt.** Detekt does not know `modules.yaml` and will not
enforce layer direction across Gradle projects.

**ArchUnit instead of Konsist.** §15.3 already chose Konsist; the rule codes
are the `ArchitectureException.rule` vocabulary.

**Copying ESLint thresholds onto Kotlin.** A use case with five constructor
ports is normal DIP here; ESLint's `max-params: 4` would punish that shape.
Function and constructor limits are split for that reason.

**MockK "only for a dummy".** A dummy that is not a port implementation lies
the day the port grows. `JobsFake` in `src/test` is the dummy (ADR-044).

**detekt-formatting alongside ktlint.** Two ktlints.

**`ktlint_official` as the profile.** This record chose it first, reasoning that
`intellij_idea` rejects a wrapped parameter list ending in a comma. That
reasoning was wrong, and it is corrected here rather than deleted: remove the
trailing comma from `Fallback`'s constructor parameter under `intellij_idea` and
ktlint answers `Missing trailing comma before ")"`. The comma never depended on
the profile, because `backend/.editorconfig` sets those properties explicitly and
a profile only supplies defaults for properties nobody set.

What the profile does decide is the shape of a class whose primary constructor
carries an annotation or a modifier. `ktlint_official` wraps such a constructor
onto lines of its own and then indents the entire class body one level further,
leaving the closing brace of the class at four spaces and its members at eight.
`platform:kernel`'s `Fallback` is exactly that shape — its constructor is
`@PublishedApi internal` so the inline chain can reach it (see that module's
README) — so under `ktlint_official` there was no formatting of that file which
ktlint would accept and a reader would recognise. Upstream declines to change it:
ktlint #2138 is parked, #3193 is closed as intended, and switching profile is the
escape both threads point at. IntelliJ formats the class a third way, so
`ktlint_official` also guaranteed a reformat fight on every save.

Paid once, measured: eleven files reformatted, most of them `arch-tests`, where
an expression body now begins on the signature line instead of the line below.

**Detekt 1.23.x.** Last 1.x stable is built against Gradle 8 / Kotlin 2.0.
This tree is Gradle 9.7 and Kotlin 2.4.10; detekt 2.0.0-alpha.6 is the
aligned line (plugin id `dev.detekt`).
