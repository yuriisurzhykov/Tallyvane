# verification — `tallyvane.verification`

`tallyvane.gradle.verification.VerificationPlugin` registers the backend
`arch` aggregator (ktlint and detekt on every leaf, `:arch-tests:test`,
`validateModuleGraph`, and this included build's `check`) and makes `check`
depend on `arch`.

There is no domain here: it is Gradle wiring. `tallyvane.root` applies it.
