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

## Where each layer's own story lives

This file is an index, not a fifth copy of what each layer's README already says. Why `contract`
was built first and is this small, why `domain` and `application` are shaped the way they are, what
each layer's own SOLID angle is, and what is still open in each — read the layer, not this file:

- [`contract/README.md`](contract/README.md) — the published surface, and the one Konsist false
  positive it hit first in this tree.
- [`domain/README.md`](domain/README.md) — the five packages, and the flat-package mistake behind
  them.
- [`application/README.md`](application/README.md) — the ports, the use-case grouping, and an open
  question about a silently recovered failure that this file alone cannot close.
- [`infrastructure/README.md`](infrastructure/README.md) — the real adapters, and why this stays one
  Gradle module rather than one per method.
