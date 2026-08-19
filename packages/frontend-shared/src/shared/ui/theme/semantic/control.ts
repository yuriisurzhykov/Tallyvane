import { defineTheme } from "design-token-engine";
import { controlContract } from "../contracts/control";

/**
 * Three heights, built from primitives the spacing scale already reserves at
 * the 4/8 rhythm rather than inventing new ones — 32, 40 and 48 pixels. No
 * theme axis: a control is the same height in light and dark, which is why
 * this sits under `semantic/` rather than `themes/`.
 *
 * `md` is the default size for every control that has one. A component
 * choosing to omit the size prop falls back to this, not to the smallest or
 * largest step, so "no opinion" and "medium" are the same thing everywhere.
 */
export const controlRole = defineTheme(controlContract, {
    heightSm: "{dimension.8}",
    heightMd: "{dimension.10}",
    heightLg: "{dimension.12}",
});
