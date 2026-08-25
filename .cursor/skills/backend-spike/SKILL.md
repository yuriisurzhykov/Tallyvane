---
name: backend-spike
description: >-
  Creates a backend spike under backend/playground/<name>/ — the Gradle module,
  the application wiring, the main function, and the dated README that records the
  question and the run output. Use when writing throwaway code to answer a question
  about real behaviour (a database, a driver, a library's actual semantics), when
  adding or changing a module under backend/playground/, or when deciding whether
  something belongs in a spike or in a test.
---

# Backend spike

A spike is code written to answer one question, kept so the answer stays checkable
instead of remembered. It lives in `backend/playground/<name>/`, prints evidence a
human reads once, and is allowed to be throwaway — but not allowed to rot.

## First: is it a spike, or a test?

Decide this before writing anything, because the answer decides where the code goes.

| | Spike | Test |
|---|---|---|
| Answers | "what does this actually do?" | "does this still hold?" |
| Read by | a person, once | CI, forever |
| Lives in | `backend/playground/<name>/` | the module's `src/test` or `src/integrationTest` |
| Asserts | nothing; it prints | an invariant |

If the answer belongs in CI, it is a test — write it there and stop. If a reviewer
would ask "why is this assertion here?", it was a spike pretending to be a test.

A spike may become a test afterwards. That is a good outcome: the spike proved the
behaviour exists, the test pins it.

## Layout

```
backend/playground/<name>/
  README.md
  build.gradle.kts
  src/main/kotlin/tallyvane/playground/<name>/<Name>Spike.kt
```

## Steps

```
- [ ] 1. Register the module in backend/settings.gradle.kts
- [ ] 2. Write build.gradle.kts
- [ ] 3. Write the spike
- [ ] 4. Write the README
- [ ] 5. Verify: check compiles it, run produces the output
- [ ] 6. Paste the real output into the README
```

### 1. Register the module

Add one line to the playground block in `backend/settings.gradle.kts`:

```kotlin
include(":playground:<name>")
```

### 2. `build.gradle.kts`

```kotlin
plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.<name>.<Name>SpikeKt")
}

dependencies {
    implementation(projects.platform.<module>)
    // Anything the spike touches directly. A module's `implementation`
    // dependencies are not transitive, so name them here even if the module
    // under test already uses them.
    runtimeOnly(libs.postgresql)
}
```

The `mainClass` is the file's JVM name: `<Name>Spike.kt` compiles to
`<Name>SpikeKt`.

### 3. Write the spike

```kotlin
package tallyvane.playground.<name>

private val access =
    DatabaseAccess(
        url = System.getProperty("spike.url", "jdbc:postgresql://localhost:5433/demo"),
        user = System.getProperty("spike.user", "demo"),
        password = System.getProperty("spike.password", "demo"),
    )

fun main(): Unit =
    runBlocking {
        // arrange, act, print
    }
```

Inputs arrive as `-Pspike.<key>=<value>`, which `tallyvane.spike` forwards as system
properties. Give every one a default that matches the README's own commands, so
`./gradlew :playground:<name>:run` works with no arguments.

### 4. Write the README

Required, and dated per `.cursor/rules/development-methodology.mdc`:

```markdown
# <name>

## YYYY-MM-DD — <the question, as a question>

Why the question was not already answered by the tests.

Commands to reproduce, including how to start whatever the spike talks to.

## What the run showed

<the actual output, pasted>

<what to read in it, and anything surprising>
```

A directory of `main` functions with no dates and no questions is
indistinguishable from abandoned code within a month.

### 5. Verify

```bash
./gradlew check                          # compiles the spike; linters do not run on it
./gradlew :playground:<name>:run         # the spike itself
```

### 6. Paste the real output

Paste what actually printed, not a cleaned-up version. If the output needed
rewording to be understandable, reword the spike and run it again — the README
should match what the next person will see.

## Printing evidence, not conclusions

A spike whose output only says "as expected" has proved nothing to its reader. Print
the numbers the conclusion rests on, and print enough of them that the interesting
failure is distinguishable from the boring one.

The concrete case this rule comes from: a transaction spike printed a running total
of committed rows after each step. Every line read `rows: 1 (expected 1)`, which
cannot distinguish "the write was rolled back" from "the write never happened" — the
one thing the spike existed to settle. Printing three numbers per step fixed it:
committed before, visible to the transaction from inside, committed after. The middle
number is the one that carries the proof.

**Observe with something independent of the machinery under test.** The same spike
counts rows over a plain JDBC connection rather than through Exposed, so the
observation cannot be fooled by the layer it is judging. When a spike exercises a
library, do not use that library to check the result.

## What `tallyvane.spike` gives you, and what it withholds

It gives Kotlin and a JVM 21 toolchain, the `application` plugin, and forwarding of
`-Pspike.*`. It withholds ktlint, detekt and warnings-as-errors.

That is deliberate and should not be "tightened" later. A spike held to production
lint is a spike nobody writes: the first one in this repository was rejected by detekt
for the literal `3`, which had nothing to do with the question it was answering.
Konsist does not scan `playground/` either — its scopes are `platform/`, `app/` and
`modules/` — so `no-top-level-functions` never meets a `fun main`. `modules.yaml` does
not govern these modules because its checks run from its own entries and there are
none here.

One rule does apply: **the spike must compile**, and `check` enforces it. A spike that
no longer builds is not a record of what was learned, it is a claim nobody can verify.
When production code moves out from under a spike, either update it or delete it —
both are honest, leaving it broken is not.

## Worked example

`backend/playground/transactions/` — whether `TransactionRunner` really commits and
rolls back against a live Postgres. Read its `README.md` for the shape of a finished
spike, including the wrong turn its own output took.
