# identity

Authentication for Milestone 0 — who is making a request, not what they may do with it.
Authorization (roles, permissions, the `Access`/`Grants` port) is a deliberately separate design
session; nothing built here should need rework when that session happens, because `identity`
publishes "who is this" through [`PrincipalResolver`](contract/src/main/kotlin/tallyvane/identity/contract/PrincipalResolver.kt)
and nothing more.

Full design: `identity_authentication_design_df4e0a44.plan.md` and
`identity_module_restructuring_fa6fa764.plan.md` (both under the author's own `.cursor/plans/`, not
in this repository — this module is their implementation). Live progress, slice by slice, decisions
made along the way, and what is still open: [backend/.plans/identity-implementation.md](../../.plans/identity-implementation.md).
This file describes only what exists in this tree today; it is not a restatement of the design.

## What exists today

```
identity/
├── contract/         Principal (sealed: User), UserId, SessionId, ResolvedPrincipal,
│                      PrincipalResolver (interface only — no implementation yet)
├── domain/           entities and value objects, grouped by the concept they belong to —
│   ├── user/          User, UserId, Email
│   ├── session/       Session, SessionId, DeviceLabel
│   ├── token/          TokenKind, TokenValue, TokenPair, HashedToken, TokenFamilyId,
│   │                    TokenFamilyState, RefreshRotationDecision, RefreshRotationPolicy
│   ├── credential/     Credential (sealed: PasswordRecord), PasswordHash
│   └── outcome/        AuthenticationOutcome, RegisterOutcome
├── application/      ports and use cases —
│   ├── port/           TokenFactory, TokenHasher, SessionStore, PasswordHasher, UserRepository,
│   │                    CredentialRepository, LoginAttempts
│   ├── password/       RegisterWithPasswordUseCase, SignInWithPasswordUseCase (+ requests)
│   ├── SessionIssuer.kt    — shared by every sign-in path, not one method's own file
│   └── IssuedSession.kt
├── infrastructure/   LoginAttemptsOverCounter, password/Argon2PasswordHasher — the module's
│                      real adapters; `password/` here is a plain package, not a Gradle module
│                      (`infrastructure` splitting into one Gradle module per method is `capture`'s
│                      own exception, ADR-073, not a general rule — see the dated correction in
│                      `backend/.plans/identity-implementation.md`)
└── web/              empty — arrives with the first real route
```

`domain`'s and `application`'s subpackages mirror the split `platform:http` already uses
(`problems/`, `status/`): a package per concept a reader would name on their own — "the token
stuff", "the password use cases" — not a flat list of every file the layer happens to contain.
`outcome/` and the two top-level `application` files (`SessionIssuer`, `IssuedSession`) stay outside
any of the narrower packages because they answer to more than one of the others: an
`AuthenticationOutcome` is not owned by `user`, `session` or `credential` more than by the other
two, and `SessionIssuer` is shared by every sign-in path this design lists (password, both Google
methods, second-factor verification), not by one of them.

No table exists yet, no route answers a request, and `PrincipalResolver` has no implementation —
calling it today would mean naming a type that does not exist. What is real is the language other
modules will eventually use to ask `identity` who somebody is, published now so `jobs` and every
capability after it can be designed against a stable contract rather than a moving one.

## Why `contract` first, and why it is this small

Every other layer depends on `platform:kernel` and, where declared, on this module's own `contract`
— never the reverse — so a contract with no domain and no application behind it is not a partial
module, it is the one layer that can exist alone by construction. Building it first, and only it,
means every later slice (`domain`'s entities, `application`'s use cases, the real
`PrincipalResolver`) is designed against a boundary that will not move under it.

`UserId` and `SessionId` live here rather than being written once in `identity:domain` and reused —
`contract-is-self-contained` forbids `contract` from importing this module's own `domain` in either
direction, and `modules.yaml` denies `domain` a dependency on `contract` just as firmly (`domain`
may depend on nothing but `platform:kernel`). The two layers are independent by design, not by
oversight: `identity:domain`'s eventual `User` and `Session` entities will need their own way to
name an id, and `application` is where the two get translated into each other, not before. This is
recorded here explicitly so the next slice does not read the two identically-shaped value objects it
is about to write as a duplication needing to be removed — it is the layer boundary being honoured,
not a mistake.

`ServicePrincipal`, the machine half of `Principal`, is named in this file and in `Principal`'s own
KDoc, and is not a case of the sealed interface yet. Adding it before a second service exists to
authenticate would mean either an uninstantiable case or a guess at what a service's identity looks
like, made without a real caller to test the guess against.

## Understandable, scalable, extensible

A reader looking for "what does `identity` let the rest of the system know" finds exactly one file,
`PrincipalResolver.kt`, and nothing it points to reaches past `contract`. Adding a second kind of
principal later is a new case on the existing sealed interface, not a redesign of the one method
every caller already depends on. Building the actual sign-in flows behind this contract — the
twelve use cases the design plan lists — changes none of the four files here, because nothing in
`application` or `infrastructure` is visible to a caller of `contract` in the first place.

## Migration and fault tolerance

No schema yet. `identity`'s tables (`identity.users`, `identity.sessions`,
`identity.credentials`, `identity.pending_authentications`, `identity.pepper_versions`) arrive with
the persistence slice, each in its own migration under this module's own schema, following
`ARCHITECTURE.md` §8.22's convention every other module will eventually use.

## The SOLID angle

**Single responsibility.** `PrincipalResolver` answers one question — who is this — and nothing in
this pass's scope asks it to answer "what can they do".

**Interface segregation.** One method. `ResolvedPrincipal` is a plain data holder, not a second
surface bolted onto the resolver interface itself.

**Dependency inversion.** Every future module that needs to know a caller's identity depends on this
interface, never on `identity`'s own `domain` or `infrastructure` — the direction Clean
Architecture and this project's own `modules.yaml` both already require, made concrete for the one
module every other capability will eventually depend on.

The remaining three principles — open/closed, Liskov — apply to the use cases and ports behind this
contract, which do not exist yet; they belong to the slices that write them, not to this one.
