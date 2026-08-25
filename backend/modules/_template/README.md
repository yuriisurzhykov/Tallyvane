# _template

The shape of a capability module. Copy this directory, rename it, rename the
`tallyvane.example.*` packages, delete the layers you do not need.

**Not part of the build.** There is no `include` for it in
`settings.gradle.kts`, so Gradle never sees it. That is intentional: a template
that compiles has to be kept compiling, and it starts collecting placeholder
code to justify itself.

```
_template/
├── contract/       interfaces and immutable data for other modules
├── domain/         entities, value objects, policies — no I/O
├── application/    use cases and ports (port/ subpackage)
├── infrastructure/ adapters; implementations internal
└── web/            routes and transport DTOs
```

## Checklist when copying

- Rename packages to `tallyvane.<capability>.<layer>`.
- Delete unused layers. `analytics` has no `contract` because nothing reads
  from it, and no `domain` because it owns no rules — that is a correct module,
  not an unfinished one.
- Move the entry in `../../modules.yaml` from `planned` to `modules`.
- Add one `include` per surviving layer to `settings.gradle.kts`.
- Give each layer its convention plugin: `pure-module` for `contract` and
  `domain`, `adapter-module` for `infrastructure`, `web-module` for `web`.
- Create the module's PostgreSQL schema in a migration, named after it.

## Where a transaction goes

The order below is not a style preference; each line of it is either enforced or
was chosen against a named alternative in
[ADR-052](../../../docs/adr/ADR-052-transaction-verdict.md).

```kotlin
suspend fun completeSignIn(request: SignInRequest): SignInOutcome {
    // 1. Decide on pure data. No port is touched, so there is nothing to undo.
    val decision = policy.decide(request, clock.now())

    if (decision is Denied) {
        // 2. A write that must outlive the refusal goes OUTSIDE the transaction.
        //    Inside a rolled-back one it would vanish, and with it the throttle.
        attempts.record(request.email, clock.now())
        return SignInOutcome.Rejected(decision.reason)
    }

    val session =
        transactions.inTransaction {
            users.upsert(decision.user)

            // 3. Events are published INSIDE: the outbox row commits with the write,
            //    so a rolled-back transaction announces nothing (§4.5).
            events.publish(UserRegistered(decision.user.id, clock.now()))

            // 4. The block says how it ends. It cannot return without saying.
            Verdict.Commit(sessions.open(decision.user.id))
        }

    return SignInOutcome.Succeeded(session)
}
```

Four things go wrong if this order is not kept, and only two of them are caught
by a machine.

**Deciding inside the transaction** is legal but must be declared:
`Verdict.Rollback(SignInOutcome.Rejected(...))` when a conflict only surfaces on
the insert. What is *not* possible is committing a refusal by accident — the
compiler will not let the block end without a verdict.

**Calling a second `inTransaction` inside the first** throws. When two writes must
be atomic they belong to one use case, not two composed ones. This also means a
method a neighbour calls through its `contract` must not open a transaction, or a
caller that already opened one fails inside somebody else's module.

**Returning `Verdict` from anything** fails `no-verdict-in-signature`. It is a
directive to one transaction, not a result that travels.

**Putting a must-survive write inside a rolled-back block** is the one nothing
catches. Rate-limit records, audit entries and anything else that has to exist
*because* the request failed belong before the transaction opens.

## The mistake this template exists to prevent

The tempting shortcut is one module per capability with the layers as packages
inside it. It looks tidier and there are five times fewer build files. It also
moves the layer boundary from the compiler to a test — and a boundary that
only a test defends is one that gets crossed on a busy afternoon and noticed
three weeks later.
