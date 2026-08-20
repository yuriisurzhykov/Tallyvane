# context-menu

Right-click actions on table rows — Tier 0. Opened by right-click or
long-press at the pointer's own position, not against a trigger edge, which
is the one respect in which this differs from `Popover`/`Menu`'s dropdown
positioning.

## What needed doing, and the one thing worth flagging about how

`@base-ui/react/context-menu` ships only its own `Root` and `Trigger`
(verified against its `index.d.ts`) and re-exports every other part —
`Portal`, `Positioner`, `Popup`, `Item`, `Separator`, ... — straight from
`@base-ui/react/menu`. `Menu.tsx` already existed in this package by the
time this component was built (a separate, parallel batch), so this file
imports those shared parts from `menu` directly and **reuses its item and
separator styling verbatim** rather than inventing a second visual language
for the same kind of list — a right-click menu and a dropdown menu are the
same kind of thing to a reader, just opened differently, and both wrap the
exact same underlying `MenuItem`. Nothing here needed reconciling after the
fact: the shared styling was written once, in `Menu.tsx`, and copied by
reference in spirit rather than duplicated by guesswork.

`z-popover` lives on the Positioner, not the Popup, for the same reason
`Popover.tsx` and `Menu.tsx` both document independently — only the
Positioner is actually `position`-having, so a `z-index` on the Popup alone
would have no effect.

## Why no `Group`/`GroupLabel`

`COMPONENTS.md`'s own purpose for this component — "right-click actions on
table rows" — describes a flat list, and no known call site groups its
items. Base UI's menu family supports grouping; this wrapper does not
expose it yet, on the Rule-of-Three reasoning `SKILL.md` §4 asks for rather
than generalising on a guessed future need.

## SOLID

Single responsibility: positioning at the pointer and the visual chrome
around a list of actions, nothing about which actions or what a "row" is —
that knowledge belongs to whatever Tier 3+ component supplies the `Item`s
as children. Liskov: an `Item` behaves identically whether it sits inside
this `ContextMenu.Popup` or inside `Menu.Popup`, because both are the same
underlying `@base-ui/react/menu` `Item` wearing the same class.
