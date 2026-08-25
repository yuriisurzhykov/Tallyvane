# ADR-051. Migrations sit in the module, are numbered by timestamp, and are applied by their own command

## Decision

Each module carries its migrations as resources under `db/migration/<module>/`.
`platform:persistence` hands Flyway a single location, `classpath:db/migration`,
and Flyway walks it — so adding a capability registers nothing anywhere. That is
the point of choosing a convention over an explicit list: the list would be a
second place to forget.

File names carry a timestamp rather than a per-module counter:
`V20260824153000__create_identity_users.sql`.

Migrations are applied by a dedicated one-shot command invoked by the deploy, not
by the application at startup. Readiness therefore *verifies* that migrations are
applied rather than applying them, which is the shape §16.6's readiness probe needs
anyway.

## Why the numbering is global, and not a matter of taste

§4.6 says `platform:persistence` "собирает их в общий поток и применяет в порядке
версий" — one ordered stream, not module after module. Two facts make that
mandatory.

Cross-schema foreign keys are allowed, and the schemas use them: `jobs.companies`
declares `user_id … references identity.users (id)`. So `identity`'s table must
exist before `jobs`' migration runs. Iterating module by module would order those
two by whatever order the modules happen to be visited, and the first foreign key
would fail.

And Flyway orders by version across all locations, not within each. Two modules
each starting at `V1__` are a duplicate-version error, not two independent
sequences. A timestamp makes the global order a property of when the migration was
written, which is also the order in which the dependency between them was created.

## A contradiction this record resolves

`ops/README.md` listed `migrations/ Flyway scripts, grouped by owning module` in
its tree while its own prose two screens later said each capability "carries its
own migration directory", matching §4.6 and §8.22. The tree was wrong and is
corrected: nothing lives under `ops/migrations/`.

## What stays from §8.22

A migration is never edited after it merges. A destructive change is split into
"add the new — switch the code — drop the old" across separate releases. Every
migration is exercised in CI against both an empty and a populated database.

`create extension if not exists citext` belongs to the first platform migration,
not to a capability and not to a manual step on the server — the same rule §8.22
already states for `vector` in Phase 2.

## What the machine checks

A migration may name only its own schema. The exemption is exactly the one §4.6
grants: a foreign key may cross a schema, a query may not, so `references
identity.users (id)` and `join jobs.companies` can sit in one file and only the
second fails. `MigrationSchemaSpec` enforces it, because the Kotlin gates read
Kotlin and a `create view` reaching into a neighbour would otherwise pass every
check this repository has — see ADR-045.

The mechanism by which Flyway discovers the per-module directories is a claim about
a library, and it is verified where it first runs rather than asserted here.

## Rejected alternatives

**`ops/migrations/<module>/`, as the old tree showed.** Puts a module's schema
history outside the module, so reading one capability means reading a directory
shared with every other, and a module stops being movable as a unit.

**An explicit list of locations passed from `app`.** Visible in a diff when a
module is added, which is a genuine argument in a repository that values exactly
that. Rejected because it is a second registration step next to
`settings.gradle.kts` and `modules.yaml`, and forgetting it fails at runtime with a
missing table rather than at build time.

**Per-module version sequences.** Reads more naturally inside one module and breaks
on the first cross-schema foreign key, as above.

**Applying migrations at application startup.** Simpler to operate with one process
and one VPS, and it was close. Rejected because it couples schema change to process
restart, gives two instances a race the day there are two, and leaves readiness
with nothing to verify — it would be reporting on work it had just done itself.
