# skip-link

WCAG 2.4.1's bypass mechanism — a link, hidden until focused, that jumps a
keyboard user past repeated navigation straight to the main content. Tier 0.

## What needed doing

Keyboard and screen-reader users need a way to skip the same repeated
navigation chrome on every page without tabbing through it every time — a
WCAG 2.4.1 requirement, not an optional nicety, and required once by the
future `AppShell`.

## What was actually done

Hidden via Tailwind's `sr-only` utility (the same one `VisuallyHidden`
already establishes as this system's pattern for offscreen-but-announced
content), revealed by its `focus:not-sr-only` variant — no custom
clip-path/positioning logic needed for exactly this pattern, since Tailwind
already ships the pair.

Positioned `fixed` at the corner with `z-toast` on focus — deliberately the
highest *named* layer below `tooltip`, not `z-modal`/`z-popover`. Reasoning
traced through `semantic/z-index.ts`'s own stacking order: a skip link that
appears on focus must sit above whatever chrome it lands on top of, and a
tooltip can still be legitimately summoned from a control on any layer
including a toast, so `z-toast` is the correct ceiling for "above everything
except the one thing that must always win."

The reveal, position, background and focus ring are all gated under plain
`:focus`, not `:focus-visible` — deliberate, not an oversight: a skip link's
entire audience is keyboard/assistive-technology users, where the two
pseudo-classes are equivalent in practice, so splitting the styling across
both would add a second trigger for no real gain.

One look, one job, no variants: this component takes only `href` and
`children`, both required, both caller-supplied — no default copy
(`COMPONENTS.md` §12 bans hardcoded user-facing strings below Tier 3) and no
default target, since the caller (a future `AppShell`) owns the actual
landmark id.

## SOLID

Single responsibility: exactly the WCAG 2.4.1 bypass mechanism, nothing
about page structure or what "main content" means on any given screen — that
knowledge stays with whatever composes this component, per its required
`href` prop.
