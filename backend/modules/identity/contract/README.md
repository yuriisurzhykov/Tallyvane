# identity:contract

`Principal` (sealed: `User`), `UserId`, `SessionId`, `ResolvedPrincipal`, `PrincipalResolver` — the
only surface another module is ever allowed to depend on for "who is making this request". No
implementation of `PrincipalResolver` exists yet; calling it today would mean naming a type nothing
constructs.

## What problem it solves, and why nothing existing could be reused

Every future module that needs to know who is making a request — `jobs`, `applications`, everything
listed under `planned:` in `modules.yaml` — has to ask `identity` without seeing `identity`'s own
`domain` or `infrastructure`. `contract-is-self-contained` (Konsist) enforces this directly: it
forbids `contract` from importing anything but `platform:kernel`/`platform:events`. This layer had to
be written whole, before anything behind it existed, not extracted from `domain` afterward — there
was nothing to extract from yet.

## Why a closed hierarchy, not one type with a capability set

`Principal` is sealed rather than one type carrying a capability set or a discriminator field, so
a use case that only ever makes sense for a human — deleting one's own account, say — can require
`Principal.User` in its own signature and let the compiler refuse a caller holding some other kind
of principal, once one exists. `backend/.plans/backend-access-and-api.md` recorded the same
reasoning for this exact question: "a closed hierarchy of four cases instead of one type with a set
of permissions, so a scenario that needs an owner does not accept an extension's token at the
compiler level".

`ServicePrincipal` — the machine-to-machine half of this hierarchy, for a second process calling
this one directly — is named in `Principal`'s own KDoc and reserved rather than added as a case
now. There is no second service in this system to authenticate yet, and a case nothing can
construct would be a promise this pass cannot keep. It becomes a real case the day a second
service needs one, not before.

## Why `PrincipalResolver` is the whole boundary, and how the real wiring will work

`platform:http` cannot depend on this contract directly — `platform` may never depend on
`modules:*` — so it exposes a generic extension point instead: "run this before every route, store
the result on the call". `server`, the composition root, is the one place both `platform:http` and
this contract are visible at once, and it is where the real implementation gets wired into that
extension point. That wiring is not this pass's slice; it arrives with the real implementation over
`SessionStore`, once one exists.

This closes option A of `backend/.plans/backend-access-and-api.md`'s "how does each module know the
user" question: a session cookie is turned into a `ResolvedPrincipal` exactly once, at the HTTP
boundary, never a second time per request.

## Why this module's `UserId`/`SessionId` duplicate `domain`'s, on purpose

`identity:domain`'s own `UserId` and `SessionId` (`domain/README.md`) look identical to the ones
here — same wrapped `Uuid`, same name. They are not the same type, and are not meant to converge:
`contract-is-self-contained` forbids `contract` importing `domain` in either direction, and
`modules.yaml` denies `domain` a dependency on `contract` just as firmly (`domain` may depend on
nothing but `platform:kernel`). Translating between the two is `application`'s job, once a resolver
exists to do the translating. Recorded here so the next reader does not see two identically-shaped
value objects and "fix" the duplication that the layer boundary requires.

## Wrong turn: a Konsist rule that had never seen this shape before

`contract-no-logic` failed on `Principal.kt`'s nested `data class User` the moment it was written.
The rule (`LayerRules.kt`) allows a nested type inside `*:contract` only when its name appears in
`NESTED_IMPL_ALLOW`, a list built for I/O-free decorators (`Cached`, `Retrying`) and never extended
to cover a sealed interface's own case — because `identity` is the first real module in this tree,
and no `*:contract` had ever nested a sealed case before it. Two fixes were weighed: widen the
Konsist rule to recognise a sealed interface's own nested case (written and verified against both
`Principal.User` and the rule's existing dirty fixture, then reverted), or accept the false positive
as a named, budgeted exception. The second won: editing a shared architecture-test file to fit one
slice's shape costs more than this slice needed, and `@ArchitectureException` exists to make that
trade-off visible instead of quietly reshaping a shared rule around one caller. Comparison and the
rejected alternative in full: [ADR-075](../../../docs/adr/ADR-075-contract-no-logic-exception-for-sealed-case.md).

## Understandable, scalable, extensible

A reader looking for "what does `identity` let the rest of the system know" finds one file,
`PrincipalResolver.kt`, and nothing it points to reaches past this layer. A second kind of principal
— `ServicePrincipal`, named in `Principal`'s own KDoc but not yet a case — is a new sealed case when
a second service exists to authenticate, not a redesign of the one method every caller already
depends on.

## Migration and fault tolerance

No schema; this layer persists nothing and calls nothing that can fail at runtime.

## The SOLID angle

**Single responsibility.** `PrincipalResolver` answers one question — who is this — never what they
may do; authorization (`Access`/`Grants`) is a deliberately separate design.

**Interface segregation.** One method. `ResolvedPrincipal` is a plain data holder, not a second
surface bolted onto the resolver itself.

**Dependency inversion.** Every future module depends on this interface, never on `identity`'s own
`domain` or `infrastructure` — the direction Clean Architecture and `modules.yaml` both already
require, made concrete for the one module every other capability will eventually depend on.

The remaining principles — open/closed, Liskov — apply once a second implementation or a second
principal kind exists; neither does yet.
