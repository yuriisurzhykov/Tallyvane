import { definePrimitives } from "design-token-engine";

/**
 * Structural lengths — how wide a page or a panel is allowed to be. Not
 * spacing: `dimension.ts`'s steps exist to be chosen between, while each of
 * these is a single load-bearing measurement.
 *
 * Keyed by the pixel measure, because that is the one thing about a raw length
 * that is factual. The name of the thing it is for lives one layer up, in
 * `semantic/layout.ts` — and keeping the two apart is what makes it visible
 * when two roles happen to want the same width, rather than each carrying its
 * own copy of the number.
 *
 * Values from docs/frontend/01-shared-design-tokens.md §9.3.
 */
export const layout = definePrimitives({
    64: "4rem",
    240: "15rem",
    1280: "80rem",
    1600: "100rem",
});
