# ADR-076. Authentication is three modules: who, how they proved it, what they were given

## Status

Accepted. Replaces the single planned `identity` module of §4 and the attempt on the
`feature/authentication` branch, which put every concern below into one module and was never
mounted.

## Context

The first attempt at authentication grew into one module of about ten thousand lines: accounts,
passwords, two Google flows, TOTP, access and refresh tokens, and their HTTP routes. Nothing in the
build stopped the session code from deciding which factors were enough, or the Google code from
issuing tokens, because inside one module every class may see every other.

The design that replaces it (ADR-077 to ADR-084) answers three different questions:

1. **Who is this person?** The account, the display name, the link to a Google subject,
   capabilities, the profile, being disabled.
2. **How did they prove it?** Factors, the sign-in attempt, purposes, versioned policies, TOTP,
   recovery codes, the delay after failures.
3. **What were they given?** Sessions, devices, revocation, tokens for the extension and the mobile
   client, the scope of each token.

## Decision

**Three capability modules, one per question.**

| Module           | Answers                     | Reads synchronously from |
|------------------|-----------------------------|--------------------------|
| `identity`       | who this person is          | —                        |
| `authentication` | how they proved it          | `identity`               |
| `sessions`       | what they were given        | `authentication`         |

`identity` keeps the name and the schema §8.3 already gives it: the word means "who", and every
other module of the system already reads it (`reads: [identity]`), so the narrow module inherits
the references rather than forcing a rename across the document.

**Dependencies point one way: `sessions → authentication → identity`.** `authentication` finds or
creates the account through the `identity` contract. `sessions` redeems a completed sign-in attempt
through the `authentication` contract and issues a session in the same transaction
(`TransactionRunner`, ADR-052); it also reads the active policy's lifetimes through that contract
on every request.

`authentication` does not know sessions exist. The module that decides whether the factors are
enough cannot grant access itself; access is granted only by a different module, and only from an
attempt the policy declared complete. A bug in one of them is not enough to open the door.

A fourth module follows when the security journal is built (ADR-083). It listens to the other three
through events and is read by the settings screen; nothing depends on it. Its name is chosen then:
module names become Kotlin packages, so the obvious `security-journal` is not available as is.

## Alternatives considered

**One `identity` module with tidy packages inside** (`account/`, `factor/`, `policy/`, `session/`).
Simplest to start, and rejected because the boundaries would rest on discipline alone. `modules.yaml`
and the Konsist rules check modules, not packages, and the previous attempt is the evidence of what
happens to a boundary nobody checks.

**Six or more small modules** (`factors`, `policies`, `attempts`, `devices`, …). Rejected: each would
be tiny, the links between them would outnumber the code inside them, and a reader would need a map
to follow a single sign-in.

**Naming the first module `accounts`.** Considered when the split was proposed and rejected once
§8.3 was read: the planned `identity` is already exactly this module, and renaming it changes dozens
of references without changing any code.

**Letting `authentication` issue the session.** Rejected for the reason above: it would put the
decision and the grant in one place. It would also make `authentication → sessions` and
`sessions → authentication` (for lifetimes) a cycle.
