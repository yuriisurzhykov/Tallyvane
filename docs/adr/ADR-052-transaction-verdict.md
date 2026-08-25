# ADR-052. A transactional block states its verdict; it does not commit by returning

## Decision

`TransactionRunner.inTransaction` takes a block returning `Verdict<T>` rather than
`T`. The block cannot reach its end without naming `Verdict.Commit` or
`Verdict.Rollback`, and `inTransaction` hands back the value either carried, so a
rolled-back block still answers its caller.

Nesting throws. A method a neighbour reaches through its `contract` must not open
a transaction.

## The mistake this forecloses

This codebase reports expected failure as a returned value, not an exception:
ENGINEERING-PRINCIPLES.md says "Inside the layers, a failure that the caller is
expected to handle is a returned value". A transaction, meanwhile, decides by
whether the block threw.

Those two rules combine badly. A use case that decides inside a transaction that
a request must be refused returns that refusal normally, the runner sees a normal
return, and everything written before the refusal commits:

```kotlin
transactions.inTransaction {
    users.insert(candidate)                      // written
    if (!allowed) SignInOutcome.Rejected         // refused
    else SignInOutcome.Succeeded(session)
}
```

No signature objects, no test fails unless somebody thought to write it, and the
row is simply there afterwards. With a `Verdict` the same code does not compile.

## The alternative that was tried first, and why it lost

The first proposal kept §6.13's signature and added a Konsist rule instead: the
block may not return a type whose name ends in `Outcome`, on the reasoning that
decisions then have to be made before the transaction and a refusal never enters
one.

It was rejected for two reasons.

It leaks. The rule keys on a naming convention, so a refusal expressed as
`Boolean`, as `null`, or as a sealed type named anything else passes untouched
while committing exactly the same partial write. That is a guard against the
idiomatic mistake, not a guarantee, and the difference matters when the question
asked was "what stops me forgetting this".

And it forbids a legitimate shape. A conflict discovered *inside* the
transaction — a unique-key violation the adapter turns into a typed
`InsertOutcome.AlreadyExists` — is naturally reported as a refusal from inside:

```kotlin
transactions.inTransaction {
    when (users.insert(candidate)) {
        InsertOutcome.AlreadyExists -> Verdict.Rollback(SignInOutcome.Rejected("email taken"))
        InsertOutcome.Inserted -> Verdict.Commit(SignInOutcome.Succeeded(sessions.open(candidate.id)))
    }
}
```

Under the rule this is illegal, and the alternatives are worse: let the exception
escape and lose the typed outcome, or check for the conflict with a separate query
first, which costs a round trip and races with the insert.

The two cannot be combined, which was proposed and examined. Under `Verdict` the
block's type is `Verdict<SignInOutcome>`, so a rule about returning `*Outcome` is
satisfied always and can never fire — and `arch-tests/README.md` is explicit that
"A rule that has never been shown to fail is not a rule — it is a comment that
happens to compile." A dirty fixture could be written, since Konsist parses
fixtures rather than compiling them, but it would describe code the compiler
already rejects. The result would be a rule in the build that protects nothing
while reading like a live guard.

## What keeps `Verdict` from becoming a second result type

ENGINEERING-PRINCIPLES.md warns that "Two competing result types in one codebase
is worse than either of them alone", and that objection was used in this same
sprint to strike a general `Outcome` from §4.2. It does not apply here for one
reason, and the reason is enforced rather than asserted: `Verdict` never crosses a
boundary. `no-verdict-in-signature` refuses it as the declared type of any
function or property, in `main` and in `test`, so it can exist only as a block's
last expression. Parameters are untouched, because `inTransaction` taking one is
the point.

The rule reads declared types, so an inferred return type on a non-public
declaration could in principle hide one. `explicitApi()` on `pure-module` forces
explicit return types on public API, which covers the cases that would actually
leak into another layer.

## Nesting, and the obligation it puts on contracts

Two transactions where one was intended means part of the work can commit while
the rest rolls back — the state the port exists to prevent. So a nested call
throws rather than quietly opening a second transaction, and when two writes must
be atomic they belong to one use case, which is the shape `web-one-usecase` and
§11.1 already require of a route.

The consequence reaches across modules: if a `contract` method opened a
transaction, any caller that had already opened one would fail inside a neighbour
for reasons it never suspected. Reads do not need a transaction, so contracts
simply do not open them.

## The hazard that no rule catches

`Rollback` discards everything, including a write meant to outlive the refusal.
Recording a failed sign-in attempt for rate limiting is the standard example: put
it inside a rolled-back transaction and it vanishes, and with it the ability to
throttle.

```kotlin
transactions.inTransaction {
    attempts.record(email, clock.now())          // erased by the rollback
    Verdict.Rollback(SignInOutcome.Rejected(reason))
}
```

Such a write belongs outside the transaction. This cannot be checked: telling a
write that must survive from one that must not requires knowing what it means, and
"inside a transaction" is a question about a function body, which Konsist reads
poorly. Unlike the case above, where review was standing in for an available
guarantee, here no guarantee is available and the record says so plainly.

## Cost

`Verdict.Commit(...)` appears at the end of every transactional block in the
codebase, and in the common case — where the refusal was decided before the
transaction ever opened — it carries no information. That is the price of the
guarantee, paid on every block, forever.

§6.13's signature changed, which is a specification amendment rather than an
implementation detail.

## Rejected alternatives

**A rollback handle passed into the block**, in the manner of Spring's
`setRollbackOnly`. Forgetting to call it is silent, which is the property the
whole record exists to remove.

**Requiring the block's return type to implement a `Transactional` interface
carrying a `commits: Boolean`.** Compile-enforced without a wrapper, but it pushes
a persistence concern into every use case's outcome type.

**Leaving it to review.** Rejected by the same standard §1.5 sets for the whole
project: "архитектурные нарушения ловятся компилятором или CI, но никогда ревью."
