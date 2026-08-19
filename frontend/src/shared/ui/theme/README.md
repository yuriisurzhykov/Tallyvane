# shared/ui/theme — design tokens

The source of truth for every visual value. Specification and reasoning live in
[docs/frontend/01-shared-design-tokens.md](../../../../../docs/frontend/01-shared-design-tokens.md).

```
tokens/       primitives — physical values, hsl() strings
contracts/    the required-role list per category, single source
themes/       themed semantics: dark.ts, light.ts, shared-roles.ts
semantic/     flat semantics with no theme axis: radius, spacing, typography, layout, z-index
components/   component tokens, namespaced, no contract
composites/   shadows, transitions, text styles
adapters/     the bridge to Tailwind and to non-CSS consumers
generated/    tokens.css and resolved.ts — committed, never hand-edited
provider/     ThemeProvider, context, the theme-switching hook
compiler.config.ts   assembles all of the above for the compiler
```

---
The theme provider is not here yet. It is a React component, and the React
version belongs to Next.js, so it arrives with Next rather than being pinned
ahead of it.
---

## Four layers, and the one rule that matters

A primitive is a value. A semantic role is a name for a use, and it may point
only at a primitive — never at another role, because a chain of roles makes it
impossible to answer "what colour is this" without walking the whole graph.
Component tokens have no contract and are free. Composites may point at either
a primitive or a role.

Components outside this directory use **semantic roles only**. Seeing
`neutral-800` in a component tells you nothing about whether it survives a
theme change; seeing `border/subtle` tells you immediately.

This is enforced, not merely asked for, and it holds for every category with no
exceptions — page widths and stacking order included. `adapters/tailwind.css`
is the only place a token can be given a class-facing name, and therefore the
only place the boundary can be breached: register
`--color-x: var(--ds-color-neutral-700)` and the class `bg-x` exists for the
whole project from then on. The generator compares every `--ds-*` the adapters
reference against the exact set of variables the primitive tiers emitted, and
fails the build on a match. It has to be an exact-membership test rather than a
pattern, because a colour role and a colour primitive both compile to
`--ds-color-*` and only the compiler can tell them apart.

Every category therefore has a semantic tier, which shapes what belongs in a
primitive. `tokens/z-index.ts` holds rungs (`0`, `10`, `20`…) and
`semantic/z-index.ts` says which layer stands on which; `tokens/layout.ts`
holds lengths keyed by their pixel measure and `semantic/layout.ts` names what
each one is for. A primitive answers "what value", a role answers "what for" —
if a primitive file starts answering the second question, its roles will be
duplicates and the layer is pulling no weight.

## The compiler is build-time only

`scripts/generate-design-tokens.ts` is the single place in the whole project
that may import the token engine. Everything at runtime reads the already
resolved `generated/resolved.ts`, which keeps reference resolution, cycle
detection and the entire validation machinery out of both bundles.

`generated/` is committed and verified: `tokens:check` fails if the files
differ from a fresh compile, so they cannot be edited by hand and cannot drift.

## Known gap against Figma

Figma variables cannot express "an alias with different opacity". The subtle
status backgrounds are written here as `alpha({color.x}, 14%)` expressions,
which resolve automatically, but in Figma they carry baked-in alpha and must be
updated by hand if the base colour changes. This is the one place the two
representations diverge structurally.
