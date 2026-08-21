# drawer

**The creation surface.** With modal windows banned outright
(`ARCHITECTURE.md` §12.9 — editing happens in place, creation happens here),
every "new item" flow in this product — capture a job by hand, log an
interview, record a communication — ends at this one component holding a
form. Tier 0: it knows it is a slide-in surface and nothing about what a
job application is.

## What needed doing

A side panel that traps focus while open, locks page scroll, dismisses on
Escape or an outside press, and returns focus to whatever opened it —
load-bearing enough that this batch's brief singled it out by name for
direct verification rather than trusting Base UI's `modal: true` default to
deliver all of that for free.

## The one default this module overrides, and why

Base UI's own `swipeDirection` defaults to `'down'` — a bottom sheet. Every
drawer in this product is a side panel, so `'right'` is this component's own
default (still Base UI's real prop, still overridable) rather than an
upstream default that would slide the wrong way for every call site here.

## A real bug, found only by rendering in an actual browser

The first draft styled the backdrop and viewport with `fixed inset-0`.
Vitest (jsdom) never caught anything wrong — jsdom does not lay out real
box geometry, so a broken utility and a working one look identical to it.
A real Chromium render told a different story: the drawer's own "Close"
button reported as outside the viewport, and inspecting the computed style
showed why — `inset-0` compiles to *nothing at all* under this project's
adapter, which deliberately clears Tailwind's bare `--spacing` multiplier so
an unnamed step cannot resolve ("anything else produces nothing", per
`tailwind.css`'s own comment). Confirmed by grepping the compiled CSS for
the literal class and finding it absent, not assumed from reading the
adapter file alone. Fixed with a small inline `{ position: "fixed", inset: 0 }`
style object — geometry, not a spacing decision, so there is no token to
name for it, the same class of exception `Grid.tsx`'s own `columns` prop
already documents. `packages/storybook/tests/e2e/drawer-keyboard.spec.ts`
is what actually caught this; the equivalent Vitest suite could not have.

## Why the width is a component token

No semantic layout *role* names "how wide a drawer is" — a global role with
one consumer would trip DS102. Width is `--ds-component-drawer-width`
(`28rem`, from layout primitive `448`). A named rem constant used to be
the lint exemption; that hole is closed.

## SOLID and fault tolerance

Single responsibility: the slide-in mechanics and its tokens, nothing about
what fills it — every feature slice supplies its own form as `children`.
Open/closed: a new "creation" flow is a new composition of `Drawer.Popup`
plus that feature's own fields, never a new prop on this component.
Dependency inversion: focus trap, scroll lock, dismissal and swipe-to-close
are all Base UI's; this file owns only tokens and the one default override
above.
