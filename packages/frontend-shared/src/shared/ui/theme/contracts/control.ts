import { defineContract } from "design-token-engine";

/**
 * Control geometry: three heights plus the two sizes every small interactive
 * control shares — the glyph inside a trigger (`icon`) and the box of a
 * checkbox / radio / slider-thumb / rating-dot (`box`). Heights existed only
 * as a mention in `COMPONENTS.md` before the first scale; `icon`/`box` closed
 * the named-constant hole that used to stand in for those two measures.
 */
export const controlContract = defineContract({
    category: "control",
    required: ["heightSm", "heightMd", "heightLg", "icon", "box"],
});
