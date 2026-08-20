# logo

The product wordmark — Tier 0, and per `COMPONENTS.md`'s "Marks and
identity" row, a deliberate single instance: "never copied into a feature."
It exists to render the product name exactly once, in exactly one place,
without the name itself ever being written into this file.

## What needed doing

`ARCHITECTURE.md` §13.4 requires the product name to live only in the
string dictionary and the build config, never in code. A component was
still needed to render *something* where the wordmark goes — `text` is the
only way this component ever sees the product name, and the caller is the
one that sources it from the dictionary. Nothing already in this tier
covers "render a single required string at a fixed visual weight and
nothing else" — `Text` would work mechanically, but using it directly at
the one call site would leave no single, name-searchable place enforcing
that the real name never gets typed into source, which is precisely what
the regression test here checks for.

## What was actually done

Hand-built, not a Base UI wrapper — there is no interactive behaviour to
delegate, only a `<span>` with one fixed visual treatment
(`text-title3 text-text-primary`) and a caller-supplied `className` for
layout only, per `COMPONENTS.md` §11. There is no variant surface (no
`size`, no `tone`) because, unlike `Text` or `Dot`, this component by design
has exactly one call site — a second size or tone would be speculative
generality for a component whose whole premise is "single instance."

`Logo.test.tsx` enforces §13.4 directly by reading this file's own source
off disk and asserting it never matches `/tallyvane/i` — a genuine
regression guard against the specific mistake this component exists to
prevent, not a generic snapshot test.

The source's own comment flags a known, not-yet-done follow-up rather than
a corrected mistake: this is a "v1 placeholder... a real SVG mark will
replace this text node," and when it does, `text` must become that
element's `aria-label` (or feed a `VisuallyHidden` fallback) instead of
being dropped — the accessible name has to survive the visual swap. That
migration has not happened yet; it is a documented constraint on whoever
does it, not a wrong turn already taken and fixed.

## SOLID

Single responsibility: rendering the wordmark at one visual weight —
nothing about what the product is actually called, which is a naming
concern that belongs to the string dictionary, not this component. Open/
closed: the planned SVG-mark migration doesn't need a new prop — `text`
stays the required accessible name regardless of what visually replaces the
current `<span>`, so the public contract is already closed against that
future change even though the implementation isn't there yet. Rule of
Three / YAGNI: no `size`/`tone` variant surface, because a component with
exactly one sanctioned call site has nothing to generalize for.
