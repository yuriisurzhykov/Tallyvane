# playground

Spikes: code written to answer one question, kept so the answer stays checkable rather
than remembered. One Gradle module per spike, `playground/<name>/`, with a `main` and a
README.

```
playground/<name>/
  README.md              the date, the question, and what the run showed
  build.gradle.kts       id("tallyvane.spike") + application { mainClass }
  src/main/kotlin/tallyvane/playground/<name>/<Name>Spike.kt
```

Run one with `./gradlew :playground:<name>:run`. Values a spike needs come through
`-Pspike.<key>=<value>`, which `tallyvane.spike` forwards as system properties.

## Why the rules here are looser, and where they are not

`tallyvane.spike` gives a spike Kotlin and a toolchain, and nothing else — no ktlint, no
detekt, no warnings-as-errors. That is not laziness about quality; it is what a spike is
for. The first one written here was rejected by detekt for the literal `3`, which had
nothing to do with the question it was answering, and a spike that costs a lint argument
is a spike nobody writes. Konsist does not see `playground/` either: its scopes are
`platform/`, `app/` and `modules/`, so `no-top-level-functions` and its neighbours never
meet a `fun main`. `modules.yaml` does not govern these modules for the same structural
reason — its checks run from its own entries, and there are none here.

One rule does apply, and it is the one that makes a spike worth keeping: **it must
compile**, and `check` sees to that. A spike that no longer builds is not a record of
what was learned, it is a claim nobody can verify. If a spike stops compiling because the
code it exercised changed, that is information — either the spike gets updated or it gets
deleted, and both are honest.

The README is not optional. A directory of `main` functions with no dates and no
questions is indistinguishable from abandoned code within a month.
