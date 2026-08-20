# live-region

A visually hidden announcement channel — Tier 0, per `COMPONENTS.md`'s
"Status and feedback" row: `aria-live="polite"` by default, `"assertive"`
where genuinely urgent, for the status updates `Toast` does not cover — a
filtered result count, a background save finishing. Composes `VisuallyHidden`
directly, the Tier-0-composing-Tier-0 case `COMPONENTS.md` §2 names
`VisuallyHidden` for explicitly.

## What needed doing

Something that announces a change to screen-reader users without a visible,
dismissible surface — the opposite half of `Toast`'s job. `Toast` is for
events with their own visible confirmation and an undo action; a live region
is for changes that are already visible on screen to a sighted user (a
result count updating as a filter chip is added) and need the same
information read aloud to someone who cannot see it, with nothing new to
look at otherwise.

## What was actually done

`VisuallyHidden`'s own `render` prop swaps its default `<span>` for a `<div
aria-live={politeness} aria-atomic="true" role={...}>` — this component
supplies the ARIA wiring and the politeness vocabulary, `VisuallyHidden`
supplies the clipping. `role="status"`/`role="alert"` are set *alongside*
the matching `aria-live` value, which is deliberate belt-and-suspenders
rather than redundant: the two mechanisms have inconsistent support across
real assistive-tech and browser pairings, and pairing them is the standard
WAI-ARIA Authoring Practices recommendation for exactly this reason, not a
guess. `aria-atomic="true"` on both so a screen reader reads the whole new
message rather than trying to diff it against the previous one.

The one behavioural contract worth stating plainly, because getting it wrong
produces a component that silently announces nothing: **this component must
stay mounted for the caller's whole lifetime**, with new `children` re-render
in place. Most assistive tech only picks up an `aria-live` region that was
already present in the accessibility tree *before* its content changed —
mounting a fresh `LiveRegion` with the message already inside it, or
unmounting it between announcements, is the standard way this exact pattern
fails to announce anything, silently, with no error anywhere. This is
documented in the component's own doc comment, not only here, since it is
the one fact a caller must get right for the component to do its job at all.

## Judgment calls made while building this component

- **`role="status"`/`"alert"` in addition to `aria-live`.** The confirmed
  decision fixed the politeness vocabulary but not whether to also set a
  role. Added for the cross-AT reliability reasoning above; it costs nothing
  and both roles carry the matching implicit `aria-live` value regardless,
  so setting both explicitly only makes the contract legible in the markup
  itself rather than relying on an implicit mapping a reader would have to
  already know.
- **No `render` prop of its own.** Unlike most of this tier's primitives,
  `LiveRegion`'s whole job is fixed semantics (a live-announcing element with
  one of two roles) — there is no known call site that would need a
  different element shape, so adding polymorphism here would be
  unused surface area (YAGNI), not a general safety margin.

## SOLID

Single responsibility: ARIA wiring for an announcement, nothing about what
is being announced or when — the caller decides both. Open/closed: a third
politeness level (there is none in the ARIA spec) would be a new map entry,
not a new conditional. Dependency inversion in the same small sense `Dot`'s
README claims for its own use of `VisuallyHidden`: this component depends on
`VisuallyHidden`'s public contract (children in, screen-reader-only content
out), not on how the clipping is achieved.
