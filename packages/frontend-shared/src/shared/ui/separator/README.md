# separator

Tier 0 — a hairline divider, styled from the `border-subtle` role, that is
either announced to assistive tech as a real `separator` or hidden from it
entirely depending on whether it's dividing meaningful content or purely
decorative space.

## What needed doing

A divider line shows up constantly — between menu sections, list rows,
sidebar groups — and each of those needs a different accessibility
treatment: a separator between two groups of settings is real structure a
screen reader should announce; a rule drawn purely to break up visual
whitespace is not. Nothing else in `shared/ui` draws a line, and hand-rolling
`role`/`aria-orientation` at each call site would mean re-deciding, every
time, whether that particular hairline counts as content.

## What was actually done

Wraps Base UI's `Separator` (ADR-031) for the real behavior — its own
`aria-orientation` and default `role="separator"` — and adds exactly one
thing on top: a `decorative` prop that Base UI's own component doesn't have.
Checked against the installed `@base-ui/react` (1.7.0)'s `Separator.d.ts`
and the upstream docs before writing anything: only `orientation`,
`className`, `style` and `render` are real props there. Radix (a prior
generation of the same idea) shipped a `decorative` prop; Base UI
deliberately doesn't, treating decorative-vs-semantic as something the
caller should express directly via ARIA rather than a convenience boolean.
This component restores that caller-facing convenience by translating
`decorative` to `role="none"` itself, relying on Base UI's own prop-merge
order to let an explicit `role` win over its internal default. Not a bug or
a wrong turn — the upstream gap was identified by reading the type
definitions before implementation, not discovered by a failing test, so
there's no correction to narrate here.

## SOLID

Single responsibility: orientation and decorative-vs-semantic translation,
nothing else — the line's colour and thickness are the one `border-subtle`
role, not a variant surface to design. Dependency inversion: the actual
`role`/`aria-orientation` wiring belongs to Base UI; this file owns only the
tokens and the one prop Base UI chose not to ship, which is also the one
thing that would need to change if a future Base UI version reintroduces
`decorative` upstream.
