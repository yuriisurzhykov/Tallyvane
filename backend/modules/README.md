# modules

Capabilities. Each one owns its data, its rules and its slice of the API, and
publishes exactly one thing outward: its `contract`.

Empty for now — the first capabilities arrive in milestone 1 (`jobs`,
`applications`, `contacts`). `modules.yaml` in the parent directory records the
designed shape of all thirteen so the map can be reviewed before any of it is
built.

`_template` is the shape to copy. It is deliberately **not** included in
`settings.gradle.kts`, so Gradle ignores it entirely and it never appears in
the build graph.

## The five layers, and what each is for

**`contract`** — the published language of the module. Interfaces and immutable
data, nothing else. It must not reference the module's own `domain`: a contract
that leaks internal types drags them into every neighbour that reads it.

**`domain`** — entities, value objects and policies. No I/O, no annotations, no
reading of the current time. A value is valid from the moment it is
constructed; there is no `validate()` to forget to call.

**`application`** — use cases and ports. A use case is one action a user can
perform, published as an interface carrying the `UseCase` marker, with its
implementation nested inside it; the interface declares exactly one method, named
for the action, and never `invoke` (ADR-053). The use case marks the transaction
boundary, asks the domain for decisions and the ports for effects. Ports are
declared here because they belong to whoever uses them, not to whoever implements
them.

**`infrastructure`** — adapters. Implementations are `internal`; the module
exposes one factory that hands them back typed as ports, so no caller can name
a concrete class even by accident.

**`web`** — routes and transport DTOs. A route parses, calls exactly one use
case, and serialises. DTOs are separate from domain types on purpose: the
domain must stay free to change without breaking a contract that a mobile
client depends on.

A capability takes only the layers it needs. Missing layers are normal.
