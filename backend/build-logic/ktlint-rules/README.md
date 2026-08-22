# ktlint-rules — `tallyvane`

Custom ktlint rules for style official ktlint does not cover. The Gradle
plugin `org.jlleitschuh.gradle.ktlint` still runs the official set;
this JAR is an extra ruleset on the same `ktlintCheck` / `ktlintFormat`
tasks. Detekt stays size and complexity. Konsist stays architecture.
That split is ADR-043.

The provider sits at the package root:
`tallyvane.gradle.ktlint.TallyvaneRuleSetProvider`. Each rule is its own
file. ktlint discovers the provider through
`META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3`.

```
TallyvaneRuleSetProvider.kt   ServiceLoader entry — register, get out
TallyvaneKtlintConst.kt       ruleset id shared by the provider and each rule
NoSingleLineKdocRule.kt       every KDoc is the multiline form
```

A first look at `.editorconfig` and at `standard:kdoc-wrapping` does not
get this: that rule only forbids a KDoc sharing a line with other code.
`/** text */` on its own line is legal there, and ktlint issue 2982 would
keep that one-line form as valid. `no-single-line-block-comment` rewrites
`/* text */` to `// text` and does not apply to KDoc. A custom rule is
the remaining place.

`tallyvane.kotlin-module` puts this project on `ktlintRuleset` (GAV
`tallyvane.gradle:ktlint-rules:0.0.0`, substituted from this included
build) and pins the engine to `ktlintEngine` in the version catalog so
the rule compiles against the same API the worker loads. This project
itself is not linted by ktlint: the convention plugin that wires the
ruleset is the one that applies ktlint, and `kotlin-dsl` on
`:conventions` still must not.

Autocorrect does not call `PsiElement.replace` or `KtPsiFactory`. ktlint's
dummy PSI has no `treeCopyHandler` extension point, and those APIs throw
`IllegalArgumentException` the moment they run — including in
`ktlint-test`. Official `no-single-line-block-comment` mutates a leaf with
`rawInsertBeforeMe` / `rawRemove`. KDoc is a `LazyParseablePsiElement`
(composite), so the wrap replaces the node with a new lazy element via
`TreeElement.rawReplaceWithList`. A first draft used `createComment` and
then `rawReplaceWithText` on the composite; neither exists on that type
without the missing extension point.

## The SOLID angle

Single responsibility is one file per rule and a provider that only
registers. Open/closed is a new `RuleProvider` line on the provider —
nothing in an existing rule is edited to add a check. Liskov is each
rule implementing the whole ktlint `Rule` contract, including
autocorrect when it claims it can. Interface segregation is why this
is not a detekt rule and not a Konsist predicate. Dependency inversion
is the ServiceLoader: `ktlintCheck` depends on the provider interface,
this class is named only in the services file.
