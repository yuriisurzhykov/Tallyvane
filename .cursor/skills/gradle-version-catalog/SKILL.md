---
name: gradle-version-catalog
description: >-
  Structures Gradle version catalogs (libs.versions.toml): grouped
  commentary headers, family-shared alias prefixes, group+name coordinates.
  Use when adding, renaming, or reviewing catalog entries, editing
  backend/gradle/libs.versions.toml, or choosing a libs.* alias.
---

# Gradle version catalog

Walk the algorithm on every add or rename, not once per file. The in-repo
worked example is [`backend/gradle/libs.versions.toml`](../../../backend/gradle/libs.versions.toml).

## Canonical shape

Three tables, `#` headers, a blank line between groups, coordinates as
`group` + `name` + `version.ref`. Layout:

```toml
[versions]
# Build & language
agp = "9.2.1"
kotlin = "2.4.10"
coroutines = "1.11.0"

# AndroidX
coreKtx = "1.19.0"
appcompat = "1.7.1"

# UI
material = "1.14.0"

# Testing
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"

[libraries]
# Kotlin coroutines & Flow.
# Flow ships inside -core (no separate artifact).
# -core:    common + JVM engine (use in pure-JVM / shared modules).
# -android: adds Dispatchers.Main backed by the Android main looper (Android modules).
# -test:    test dispatchers / runTest (test source sets only).
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# AndroidX
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }

# UI
material = { group = "com.google.android.material", name = "material", version.ref = "material" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

`[versions]` groups by purpose (`Build & language`, `Testing`). `[libraries]`
groups by family. The `# Testing` block above is layout only — mixed prefixes
in one library group fail the prefix rule (see Wrong / correct).

A header may carry a short note when the group is not obvious from the
aliases, the way the kotlinx block explains `-core` / `-android` / `-test`.
Do not re-derive the whole decision in the catalog; one or two lines.

## Prefix rule

Every alias in a `[libraries]` group starts with the same prefix. The header
names that family:

- junit → every alias starts with `junit`
- kotlinx → header `kotlinx`, aliases `kotlinx-`
- gradle → header `Gradle`, aliases `gradle-` (plugin JARs on a
  convention-plugin classpath)

Not "testing" or "utils". Family is the prefix siblings would share.

## Thinking algorithm

1. **Which table?** Runtime or test JAR → `[libraries]`. Plugin id applied
   with `plugins {}` → `[plugins]`. Shared version number → `[versions]`
   only (one key, many `version.ref`s).
2. **Which family?**
   - Maven family (`kotlinx`, `kotest`, `androidx`, `junit`) when that is
     how the artifacts are published.
   - **Exception (this repo):** a Gradle plugin JAR consumed *as a library*
     on the build-logic classpath is family `gradle`, even if Maven group is
     `org.jetbrains.kotlin` / `dev.detekt`. The three current plugin JARs
     are this case.
   - `[plugins]` family is the tool (`kotlin`, `detekt`, `ktlint`). Never
     `gradle-` — the whole table is already Gradle plugins.
3. **Find or open the group.** A `# Family` header, blank line above it. A
   group of one is valid (`konsist`, `snakeyaml`). Do not append to a
   neighbour group because the file is small.
4. **Name the alias.** `{prefix}-{role}` kebab-case. Every alias in the
   group starts with that prefix. Version keys stay short (`kotest`, not
   `kotest-runner-junit5`); one version key per shared number.
5. **Write the coordinate.** Always
   `{ group = "...", name = "...", version.ref = "..." }`. Never
   `module = "g:a"`.
6. **Fix call sites.** Hyphens become dots:
   `gradle-kotlin-plugin` → `libs.gradle.kotlin.plugin`.
   `findLibrary("gradle-kotlin-plugin")` must match the alias string
   exactly.
7. **Stop.** Do not add dependencies "while we're here." Do not prefix
   `[plugins]` with `gradle-`.

## Wrong / correct

### Flat dump, `module =`

Wrong:

```toml
[libraries]
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }
```

Correct: headers, family prefixes, `group` + `name`. See
`backend/gradle/libs.versions.toml`.

### Purpose bucket, mixed prefixes

Wrong — the Testing block from the layout template, which the prefix rule
forbids:

```toml
# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
```

Correct — family header + shared prefix:

```toml
# JUnit
junit = { group = "junit", name = "junit", version.ref = "junit" }

# AndroidX Test
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
```

`[versions]` may still group those numbers under `# Testing`. The prefix
rule applies to library aliases, not version keys.

### Tool-first names for plugin JARs on the build-logic classpath

Wrong:

```toml
# Kotlin
kotlin-gradle-plugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }

# Detekt
detekt-gradle-plugin = { group = "dev.detekt", name = "dev.detekt.gradle.plugin", version.ref = "detekt" }
```

Correct — family is Gradle because these are plugin JARs used as libraries:

```toml
# Gradle
gradle-kotlin-plugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
gradle-detekt-plugin = { group = "dev.detekt", name = "dev.detekt.gradle.plugin", version.ref = "detekt" }
```

### `[plugins]` prefixed `gradle-`

Wrong:

```toml
[plugins]
gradle-kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

Correct:

```toml
[plugins]
# Kotlin
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

### Purpose prefix instead of family

Wrong: `testing-kotest-runner-junit5`.

Correct: group `# Kotest`, alias `kotest-runner-junit5`.
