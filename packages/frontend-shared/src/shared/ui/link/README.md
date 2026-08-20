# link

Inline navigation text, and the `render` target when `Button` must actually
be an anchor. Tier 0.

## What needed doing

Navigation needs to read as a link (accent colour, underline) without every
call site re-deriving that styling by hand, and `Button`'s own `render` prop
needs a concrete anchor-shaped component to compose into
(`<Button render={<Link href="/jobs" />} />`) rather than a bare `<a>` with
no tokens.

## What was actually done

No Base UI primitive exists for a plain link (`COMPONENTS.md`'s Actions row
lists `Base: —` for it), so it's built via `@base-ui/react/use-render` +
`mergeProps` directly — the same polymorphism mechanism `Text`/
`VisuallyHidden` already use, since there's no real interaction machinery
here worth delegating to a headless library (a link's only behavior is
"navigate," which `<a href>` already gives for free).

Deliberately one visual form: no `tone`/`size` props. Every known call site
so far wants the same accent-text-plus-underline treatment, and adding a
variant surface on a guess would be exactly the kind of speculative
generality `component-authoring/SKILL.md`'s YAGNI/Rule-of-Three section
warns against.

## SOLID

Single responsibility: link styling and `render`-prop polymorphism, nothing
about where a link points or why — `href` and all native anchor props pass
through untouched. Open/closed: a future second visual form (if a real call
site needs one) is a new prop, not a rewrite of the existing one, since
`useRender`'s prop-merging already keeps the door open for an explicit
`render` override at any call site today.
