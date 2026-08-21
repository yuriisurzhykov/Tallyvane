import { defineTheme } from "design-token-engine";
import { controlContract } from "../contracts/control";

/**
 * Control geometry: three heights plus the two sizes every small
 * interactive control shares — the glyph inside a trigger, and the box of a
 * checkbox/radio/slider-thumb/rating-dot. Built from primitives the spacing
 * scale already reserves at the 4/8 rhythm rather than inventing new ones.
 *
 * No theme axis: a control is the same size in light and dark, which is why
 * this sits under `semantic/` rather than `themes/`.
 *
 * `md` is the default size for every control that has a height. A component
 * choosing to omit the size prop falls back to this, not to the smallest or
 * largest step, so "no opinion" and "medium" are the same thing everywhere.
 */
export const controlRole = defineTheme(controlContract, {
    heightSm: "{dimension.8}",
    heightMd: "{dimension.10}",
    heightLg: "{dimension.12}",
    /** 16px at the default root — the established in-control glyph size. */
    icon: "{dimension.4}",
    /** 20px — checkbox / radio ring / slider thumb / rating-scale dot. */
    box: "{dimension.5}",
});
