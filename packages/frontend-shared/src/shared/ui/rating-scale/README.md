# rating-scale

A 1–5 self-rating, generic because the scale is generic — Tier 0. Used by
four unrelated fields (interest, fit, interview confidence, question
difficulty, per `COMPONENTS.md`'s own row); the label is not this
component's business.

## What needed doing

All four call sites need the exact same shape — five discrete options, one
choice, a Likert-style self-rating rather than a public product-review
widget — and none of them should have to re-derive the same visual
language or the same controlled/uncontrolled footgun independently.

## What was actually done

Five dots in a row, deliberately not digits and not stars: visually
related to `Dot` (a solid filled circle carrying meaning), the same reading
a printed 1–5 Likert scale gets on paper. Each dot is `--control-box`, the
same role as `Checkbox`/`Radio`/`Slider`. **Not a cumulative fill** — this
was a real judgment call, since the confirmed brief said "toggleable dots"
without spelling out whether choosing 4 should also visually fill 1–3
(the star-rating convention). A star's fill-up-to-N reads as a public,
comparative rating; a Likert scale is a single discrete pick with no
implied ordering weight between "3" and "the three dots before it", so only
the one chosen dot fills — the rest stay plain rings, exactly the
distinction the brief's own "not stars" already draws. Flagged explicitly
under "Judgment calls" in this batch's authoring report.

Wraps `@base-ui/react/radio-group` and `@base-ui/react/radio` directly
rather than composing this same batch's own `Radio`/`RadioGroup`: the two
need incompatible visual treatments (`Radio`'s two-part ring-plus-inner-dot
versus a single dot that is either a ring or a solid fill here), and
forcing a shared visual slot into `Radio` for one very differently-shaped
consumer would be over-engineering for two known call sites
(`.cursor/skills/component-authoring/SKILL.md` §4, YAGNI/KISS). Reusing
Base UI's primitives directly still gets the real behaviour — arrow-key
roving focus between the five dots, Space to select — for free, verified
the same way `RadioGroup.test.tsx` verifies it for `Radio`.

`getValueLabel: (value) => string` names each dot for assistive tech
(`aria-label`, e.g. "3 of 5") rather than this component holding any copy
of its own (`COMPONENTS.md` §12) — mirrors Base UI's own
`Slider.Thumb`'s `getAriaValueText` callback-prop shape rather than
inventing a new one.

### A real footgun, caught and engineered around before it shipped

The confirmed spec's `value: 1|2|3|4|5|undefined` needs a genuinely
controlled scale to be able to start unrated. Reading
`@base-ui/utils/useControlled.js` directly (not assumed from the `.d.ts`)
showed why that is not free: it decides whether a component is controlled
only once, on its first render, by checking `value !== undefined` — the
same limitation React's own docs describe for `<input value={undefined}>`.
Passing `value={undefined}` on the first render to represent "controlled,
currently unrated" would silently and permanently lock this component into
uncontrolled mode; every later `value` update from the caller would then be
ignored with no error anywhere. `0` — never a real `RatingValue`, which
starts at 1 — is used as the value actually handed to Base UI's
`RadioGroup` for "nothing selected", keeping `value !== undefined` true
from the very first render regardless of whether the caller's current
rating is set. `toInternalValue`/`toPublicValue` are the only two places
this translation happens. `RatingScale.test.tsx`'s "reflects a controlled
value update from the caller after starting unrated" test exists
specifically to prove this — it would fail immediately if `undefined` were
passed straight through instead.

## SOLID

Single responsibility: the five-dot visual language and the
value-translation footgun above, nothing about what a 3 means for any
particular field — every one of the four entity components that use this
supplies its own `label`/`getValueLabel` text. Dependency inversion: the
actual selection state machine (roving focus, arrow keys, Space to select)
is Base UI's `RadioGroup`/`Radio`; this file's job is the public API shape
and the internal-value translation on top of it.
