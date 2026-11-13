# heading

A real heading, at a stated level — Tier 0, in "Typography and text".

## What needed doing

`Text` deliberately never renders a heading tag: a variant is how text looks, and a heading is where it sits
in the page's outline. The rule that raw elements are banned outside `shared/ui` left screens with no way to
say "this is the page's heading" at all, so pages either went without one or broke the rule.

## What was actually done

`Heading` takes a `level` (1 to 4) and a `variant`, and renders `Text` into the matching element. Level and
variant are separate props on purpose: the outline decides the first and the design the second, and they
rarely agree (a page title and a card title can look alike and sit at different levels). It adds nothing else:
no colour, no spacing.
