import { definePrimitives } from "design-token-engine";

/**
 * Colour primitives — physical values only. Never referenced by a component:
 * everything above this layer speaks in semantic roles (see `../themes/`).
 *
 * Every value is a plain `hsl()` **string**, not a decomposed `{h,s,l}` object.
 * A string literal gets a colour swatch in the editor gutter; an object does
 * not, and this is a file you read by eye far more often than by API.
 *
 * Specification and the reasoning behind every choice:
 * docs/frontend/01-shared-design-tokens.md
 */
export const color = definePrimitives({
    /**
     * One hue across the whole ramp — 30 degrees — with saturation between
     * three and eight percent, falling toward the dark end.
     *
     * The first version of this scale did the opposite: saturation *rose*
     * toward black, up to twelve percent, on the reasoning that dark steps
     * otherwise flatten into a dead grey. Built and looked at, that turned out
     * to be wrong — twelve percent on a near-black reads as brown, not as warm
     * grey, and under an amber accent the whole interface came out looking
     * like an app for beekeepers. Warmth here has to be a whisper.
     */
    neutral: {
        0: "hsl(30 8% 99%)",
        50: "hsl(30 7% 97%)",
        100: "hsl(30 6% 94%)",
        200: "hsl(30 6% 88%)",
        300: "hsl(30 5% 83%)",
        400: "hsl(30 4% 68%)",
        500: "hsl(30 3% 54%)",
        600: "hsl(30 3% 42%)",
        700: "hsl(30 4% 31%)",
        800: "hsl(30 4% 22%)",
        900: "hsl(30 4% 15%)",
        950: "hsl(30 4% 10%)",
        1000: "hsl(30 5% 7%)",
    },

    /**
     * Amber means "this needs you" and nothing else. It is deliberately NOT
     * the brand accent — that role is monochrome, see `../themes/shared-roles.ts`.
     *
     * Amber originally carried both meanings, on the reasoning that in a job
     * tracker the call to action and "pay attention to this" are the same
     * thing. That was economical and wrong: once buttons, badges and the
     * progress bar were all amber, amber stopped meaning anything. A signal
     * only works while it stays rare.
     */
    amber: {
        50: "hsl(37 75% 96%)",
        100: "hsl(37 72% 90%)",
        200: "hsl(37 70% 82%)",
        300: "hsl(37 72% 74%)",
        400: "hsl(37 74% 69%)",
        500: "hsl(37 75% 64%)",
        600: "hsl(37 78% 54%)",
        700: "hsl(37 80% 42%)",
        800: "hsl(37 82% 30%)",
        900: "hsl(37 85% 20%)",
        950: "hsl(37 88% 12%)",
    },

    /**
     * Success. Pulled toward olive; a pure green sits on the warm base like a sticker.
     * */
    green: {
        50: "hsl(131 60% 96%)",
        100: "hsl(131 55% 90%)",
        200: "hsl(131 50% 82%)",
        300: "hsl(131 52% 75%)",
        400: "hsl(131 52% 71%)",
        500: "hsl(131 53% 67%)",
        600: "hsl(131 55% 56%)",
        700: "hsl(131 58% 44%)",
        800: "hsl(131 62% 32%)",
        900: "hsl(131 68% 21%)",
        950: "hsl(131 72% 12%)",
    },

    /**
     * Danger. Brick rather than scarlet, and the light steps are held back from turning pink.
     * */
    red: {
        50: "hsl(0 85% 97%)",
        100: "hsl(0 82% 92%)",
        200: "hsl(0 80% 85%)",
        300: "hsl(0 78% 76%)",
        400: "hsl(0 76% 68%)",
        500: "hsl(0 74% 60%)",
        600: "hsl(0 70% 50%)",
        700: "hsl(0 68% 42%)",
        800: "hsl(0 65% 32%)",
        900: "hsl(0 62% 22%)",
        950: "hsl(0 60% 12%)",
    },

    /**
     * Information. Slate, not blue — a saturated blue is the one colour that refuses to sit
     * on a warm base.
     * */
    blue: {
        50: "hsl(205 30% 96%)",
        100: "hsl(205 28% 90%)",
        200: "hsl(205 26% 82%)",
        300: "hsl(205 22% 70%)",
        400: "hsl(205 24% 60%)",
        500: "hsl(205 26% 48%)",
        600: "hsl(205 28% 38%)",
        700: "hsl(205 30% 30%)",
        800: "hsl(205 34% 22%)",
        900: "hsl(205 38% 15%)",
        950: "hsl(205 42% 9%)",
    },

    /**
     * Overlays are a scale of intensity, not of colour. One step number means
     * the same visual strength over any base; the theme chooses the base, never
     * the strength. That is why a hover state can be the same step in both
     * themes and still be correct in each.
     */
    overlayWhite: {
        4: "hsl(0 0% 100% / 4%)",
        8: "hsl(0 0% 100% / 8%)",
        12: "hsl(0 0% 100% / 12%)",
        16: "hsl(0 0% 100% / 16%)",
        24: "hsl(0 0% 100% / 24%)",
        32: "hsl(0 0% 100% / 32%)",
    },
    overlayBlack: {
        4: "hsl(0 0% 0% / 4%)",
        8: "hsl(0 0% 0% / 8%)",
        12: "hsl(0 0% 0% / 12%)",
        16: "hsl(0 0% 0% / 16%)",
        24: "hsl(0 0% 0% / 24%)",
        32: "hsl(0 0% 0% / 32%)",
    },

    /**
     * Scrim is composited against each theme's own base rather than plain
     * black or white, which is why it cannot be a step of the overlay scale.
     */
    scrim: {
        dark: "hsl(30 5% 7% / 80%)",
        light: "hsl(30 8% 99% / 80%)",
    },
});
