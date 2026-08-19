import { defineComposite } from "design-token-engine";

/**
 * The semantic layer for type: a size, its leading, its weight and its tracking
 * under one name, so a caller applies one token instead of assembling four and
 * getting one of them wrong.
 *
 * Every name here has a matching entry in `tokens/typography.ts`'s size scale,
 * one to one. That correspondence is the point of naming sizes by role: the
 * question "what size is a card title" has exactly one place to look, and no
 * style has to share a size with an unrelated one just because they happened to
 * land on the same step.
 *
 * Leading tightens as type grows. The same ratio that keeps body text readable
 * leaves a heading looking like two disconnected lines, and tracking moves the
 * opposite way for the same reason — large type is set tighter, small type
 * needs the air.
 */
export const textStyles = defineComposite("textStyle", {
    pageTitle: {
        size: "{typography.size.pageTitle}",
        line: "{typography.lineHeight.tight}",
        weight: "{typography.weight.semibold}",
        tracking: "{typography.tracking.tight}",
    },
    sectionTitle: {
        size: "{typography.size.sectionTitle}",
        line: "{typography.lineHeight.snug}",
        weight: "{typography.weight.semibold}",
        tracking: "{typography.tracking.snug}",
    },
    cardTitle: {
        size: "{typography.size.cardTitle}",
        line: "{typography.lineHeight.snug}",
        weight: "{typography.weight.medium}",
        tracking: "{typography.tracking.snug}",
    },
    body: {
        size: "{typography.size.body}",
        line: "{typography.lineHeight.normal}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
    bodySmall: {
        size: "{typography.size.bodySmall}",
        line: "{typography.lineHeight.normal}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
    /** Field labels and column headers — the only style carrying extra tracking, because it is set small and often in capitals. */
    label: {
        size: "{typography.size.label}",
        line: "{typography.lineHeight.snug}",
        weight: "{typography.weight.medium}",
        tracking: "{typography.tracking.wide}",
    },
    caption: {
        size: "{typography.size.caption}",
        line: "{typography.lineHeight.normal}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
    /** Salary figures, dates, identifiers. The only style that names a family, and the sole consumer of the mono face — which is why `app/fonts.ts` loads exactly one weight of it. */
    numeric: {
        family: "{semantic.typography.familyNumeric}",
        size: "{typography.size.numeric}",
        line: "{typography.lineHeight.normal}",
        weight: "{typography.weight.regular}",
        tracking: "{typography.tracking.normal}",
    },
});
