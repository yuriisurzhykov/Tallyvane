import { defineComposite } from "design-token-engine";

/**
 * The semantic layer for type: a size, its leading, its weight and its tracking
 * under one name, so a caller applies one token instead of assembling four and
 * getting one of them wrong.
 *
 * Size and line always come from the same step of their scales — the two were
 * chosen against each other, and separating them is how vertical rhythm
 * quietly breaks. Tracking tightens as type grows, for the same reason it
 * loosens as type shrinks: the letter spacing that keeps eleven-pixel text
 * legible makes a thirty-two-pixel heading look scattered.
 *
 * Names follow §8. Several styles deliberately share a size — `body` and
 * `bodyStrong` are one step apart in weight only — which is exactly what a
 * numbered size scale allows and role-named sizes would have forced into a
 * duplicate.
 */
export const textStyles = defineComposite("text", {
    /** Large analytics figures and empty-state headings. */
    display: {
        size: "{typography.size.8}",
        line: "{typography.line.8}",
        weight: "{typography.weight.semibold}",
        tracking: "{typography.tracking.tightest}",
    },
    /** Screen heading. */
    title1: {
        size: "{typography.size.7}",
        line: "{typography.line.7}",
        weight: "{typography.weight.semibold}",
        tracking: "{typography.tracking.tighter}",
    },
    /** Section heading. */
    title2: {
        size: "{typography.size.6}",
        line: "{typography.line.6}",
        weight: "{typography.weight.semibold}",
        tracking: "{typography.tracking.tight}",
    },
    /** Card heading. */
    title3: {
        size: "{typography.size.5}",
        line: "{typography.line.5}",
        weight: "{typography.weight.semibold}",
        tracking: "{typography.tracking.snug}",
    },
    body: {
        size: "{typography.size.4}",
        line: "{typography.line.4}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
    bodyStrong: {
        size: "{typography.size.4}",
        line: "{typography.line.4}",
        weight: "{typography.weight.medium}",
        tracking: "{typography.tracking.normal}",
    },
    /** Dense tables and metadata. */
    small: {
        size: "{typography.size.3}",
        line: "{typography.line.3}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
    caption: {
        size: "{typography.size.2}",
        line: "{typography.line.2}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
    /** Small capitalised headings above a block — the one style set with extra tracking, because capitals at eleven pixels close up without it. */
    overline: {
        size: "{typography.size.1}",
        line: "{typography.line.1}",
        weight: "{typography.weight.medium}",
        tracking: "{typography.tracking.wide}",
    },

    /**
     * Not in §8, and added because §8 asks for something it never defines:
     * tabular figures are required in every table, sum and date, and nothing
     * there carries them. Shares `small`'s step, since a figure column sits
     * inside dense tables.
     */
    numeric: {
        family: "{semantic.typography.familyNumeric}",
        size: "{typography.size.3}",
        line: "{typography.line.3}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
});
