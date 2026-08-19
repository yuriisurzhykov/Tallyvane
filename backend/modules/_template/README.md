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

## The mistake this template exists to prevent

The tempting shortcut is one module per capability with the layers as packages
inside it. It looks tidier and there are five times fewer build files. It also
moves the layer boundary from the compiler to a test — and a boundary that
only a test defends is one that gets crossed on a busy afternoon and noticed
three weeks later.
