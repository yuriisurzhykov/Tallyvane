# ADR-042. Pixel estimate for a virtualizer is a JS number, not a CSS variable

## Decision

`@tanstack/react-virtual`'s `estimateSize` callback must return a number of
CSS pixels. That engine does not read CSS custom properties, so the seed
value for `DataTable`'s row height (`ROW_HEIGHT_PX`) stays a JS number even
though the painted row uses `var(--control-height-sm)`.

The number is **not** an exemption for inline `style={{}}`. Row CSS goes
through the token. The number exists only as the virtualizer's initial
estimate; `measureElement` corrects drift (non-default root font, zoom)
once a row mounts.

The silence for this one JS number is a complete `@architecture-exception`
naming this ADR. A named constant, `n + "px"`, or Lucide `size={16}` is
still a hardcoded dimension everywhere else.

## Why not a CSS variable in JS

Reading `getComputedStyle(...).getPropertyValue("--control-height-sm")`
during render is layout-forcing, races the first paint (the token may not
be computed yet), and still has to parse `"2rem"` into pixels. The
virtualizer needs a number *before* it can place rows. Seeding 32px — the
same step `--control-height-sm` resolves to at the default root — and
correcting via `measureElement` is the cheaper, already-wired path.

## Rejected alternatives

**Named constant as a lint exemption.** That was the previous official
bypass (`const BOX_SIZE = "1.25rem"`). An agent used the same hole on
`N + "px"`. A constant is still a hardcoded value; the rule is now
fail-closed.

**New layout/control token consumed only by JS.** A token the CSS never
reads is a second source of truth that will drift from `--control-height-sm`.

**`eslint-disable`.** An agent will copy it. The architecture-exception
form requires an ADR id that architecture tests can check exists.
