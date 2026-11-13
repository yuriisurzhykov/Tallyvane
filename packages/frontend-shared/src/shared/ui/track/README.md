# track

A route drawn as stations on a line — Tier 0, in the design canvas's home as "the route to the next role".

## What needed doing

The home shows where a person is on the way to a goal: some stops are behind them, one is next, the rest are
ahead, and the goal is at the end. A row of badges said the same thing with the shape of a tag cloud and none
of the sense of travel.

## What was actually done

`Track` takes stops, each with a label and one of four states (`done`, `now`, `ahead`, `end`), and draws an
ordered list. Each stop owns the line segment that arrives at it, so the line is solid up to the stop
reached and faint beyond it, with no absolutely-positioned overlay to keep in step with the stops. State is
carried by shape and size as well as colour (filled, ringed and larger, empty, square), so it reads without
colour. The strip scrolls sideways when stops do not fit, and the caller names it with `label`.

It does not know what a stop is. The mapping from a skill's standing to a state is the caller's.
