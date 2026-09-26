# ADR-075. `Principal.User`'s nesting is an accepted `contract-no-logic` exception, not a rule change

## Decision

`identity:contract`'s `Principal.User` — a `data class` holding one immutable field, the single
case of `sealed interface Principal` — carries `@ArchitectureException(rule = "contract-no-logic",
adr = "ADR-075", reason = ...)`. The Konsist check itself, `LayerRules.kt`'s `contractNoLogic`, is
left exactly as it already reads. This is the first use of `@ArchitectureException` anywhere in
this codebase's production tree; the budget of ten now has one spent.

## Why the check flags this at all

`contractNoLogic`'s "nested" branch accepts a nested class inside `*:contract` only when its name
is a top-level declaration in the same file or appears in `NESTED_IMPL_ALLOW` — a list built for an
unrelated purpose, the I/O-free decorators (`Cached`, `Retrying`) `nested-impl-is-pure` also reads
from. `User` satisfies neither: it is nested inside `Principal`, not top-level, and its name is not
on that list. ARCHITECTURE.md §15.3 documents the rule as "no use case; nested I/O-free decorators
are allowed" — silent on a closed-hierarchy case, because `identity` is the first capability in this
tree to populate a `*:contract` with a sealed type at all, so nothing had exercised this shape
against the check before.

## Why an exception rather than widening the check

A version of this record first proposed narrowing `contractNoLogic`'s "nested" branch to also
accept a class that is a case of a `sealed interface` declared in the same file — implemented,
verified to compile, and confirmed by rerunning `ArchitectureRulesSpec` that it neither let the
existing dirty fixture (`JobCaptureUseCase`, a use case declared in `contract`) go unnoticed nor let
an unrelated nested class slip through. That version was reverted at the author's explicit
instruction in favour of this one.

The trade-off between the two is real and is recorded here rather than only in the conversation
that decided it. A rule change fixes the false positive for every future `*:contract` that reaches
for the same shape, at the cost of touching shared verification code every module in this
repository is checked against. An annotated exception costs nothing shared — it changes one
declaration and spends one line of a ten-slot budget that has never been touched before — at the
price of paying that same line again the next time a `*:contract` needs a sealed case, unless a
later pass revisits the check directly.

## Rejected alternatives

**Widening `contractNoLogic`'s "nested" check**, as above. Correct and smaller in the long run, but
a change to shared architecture verification is a larger, less reversible move than one annotation,
and this pass does not need to make it to get `identity:contract` compiling.

**Restructuring `Principal.User` as a top-level class implementing `Principal`, instead of a nested
case.** Avoids the check entirely without touching `arch-tests`, but breaks the one idiom every
closed outcome hierarchy in this codebase already uses — `Verdict.Commit`/`Verdict.Rollback`, and
the design plan's own `AuthenticationOutcome`/`SecondFactorOutcome` — for a reason that has nothing
to do with the design of `Principal` itself, only with dodging a check. A rule a codebase has to be
restructured around, rather than one that is fixed or explicitly excepted, is the wrong kind of
pressure to leave standing.

## Consequences

The next `*:contract` in this codebase that declares a sealed hierarchy with a nested case — a
future module's own `Principal`-shaped type, or `identity:contract` itself if it ever grows a
second sealed type — will hit the identical false positive and need its own
`@ArchitectureException`, spending its own slot of the budget of ten. That is a known, named cost
of this decision, not a surprise for whoever hits it next; this record is what they will find
pointing back at it.
