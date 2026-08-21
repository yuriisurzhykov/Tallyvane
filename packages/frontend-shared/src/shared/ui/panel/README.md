# panel

`Surface` with header, body and optional footer slots — Tier 0. Composes
`Surface` directly, per `COMPONENTS.md` §2's "a Tier 0 primitive may compose
another Tier 0 primitive" rule: `Surface` carries no domain knowledge and
imposes no competing visual decision, so reusing it here is DRY, not a tier
violation.

## What needed doing

Almost every card-shaped piece of content in this product is not just a
background — it is a background with an optional title above the content
and optional actions below it: a settings section, a summary card, a
sidebar block. Building that shape by hand at every call site would mean
re-deciding, every time, which divider token separates a header from a
body, and `Surface` on its own only ever solves the background/border/
radius part of the problem.

## What was actually done

`Panel` renders `Surface` with `variant` proxied straight through — never
fixed to one value, since which surface level a header/body/footer card
sits at is a decision `Surface` already owns, not one this component should
re-litigate — and up to three structural children: an optional `header`, a
required body (`children`), and an optional `footer`, divided by this
package's own `Separator` wherever two of them are actually adjacent (Tier
0 composing Tier 0 a second time, the same "reuse a hairline instead of a
second `border-t` implementation" reasoning `Menu.tsx`'s own `ItemSeparator`
already establishes). No separator renders at all when a panel has only a
body — verified by a dedicated test, since "no header, no footer" is the
plain-card call site and it must not gain phantom dividers.

**Judgment call: `header`/`footer` as plain `ReactNode` props, not compound
parts.** The obvious alternative was a `Panel.Root`/`Panel.Header`/
`Panel.Body`/`Panel.Footer` compound API, matching how `Menu` and (in this
same batch) `Accordion`/`Tabs` expose their own parts. Rejected: `SKILL.md`
§3.2 reserves a Context-backed compound API for a component "genuinely
drowning in configuration," with real structural variability across its
parts to coordinate — none of that is true here. The three slots are
stable, never interact with each other, and always render in the same
order; wrapping that in `Root`/`Header`/`Body`/`Footer` would be exactly the
"pure ceremony" §3.2 warns against for "two or three stable props with no
structural variation." `Button`'s own `leadingIcon`/`trailingIcon` sitting
next to a required `children` is the direct sibling precedent already in
this package for "optional named slot beside required content," and this
component follows it rather than inventing a second convention for the
same shape.

No real wrong turn to report: the plain-props shape was the first
candidate considered and held up under the SKILL.md test above, so there
was nothing to correct after the fact.

## SOLID

Single responsibility: three structural slots and the dividers between
whichever are present, nothing about what fills them or what a "panel"
means in any given feature. Open/closed: a fourth slot, if one is ever
needed, is a new optional prop and a new conditional block — the existing
header/body/footer wiring never has to change to add it. Dependency
inversion in the same sense `Menu.tsx`'s `ItemSeparator` already
establishes: this component depends on `Surface`'s and `Separator`'s public
contracts (a `variant` in, a themed box out; nothing in, a themed hairline
out), never on how either achieves its own tokens.
