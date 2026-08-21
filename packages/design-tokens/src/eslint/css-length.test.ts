import { describe, expect, it } from "vitest";
import { isDimensionPropertyKey, isHardcodedCssLength, isResourceCssValue, REACT_PX_PROPERTY } from "./css-length";

describe("css-length", () => {
    it("treats var/calc/clamp/min/max and ch as resources, not hardcodes", () => {
        expect(isResourceCssValue("var(--control-box)")).toBe(true);
        expect(isResourceCssValue("calc(3 * var(--ds-text-body-line))")).toBe(true);
        expect(isResourceCssValue("0 0 var(--control-height-sm)")).toBe(true);
        expect(isResourceCssValue("70ch")).toBe(true);
        expect(isHardcodedCssLength("var(--control-box)")).toBe(false);
        expect(isHardcodedCssLength("70ch")).toBe(false);
    });

    it("treats a bare length, including inside a flex shorthand, as hardcoded", () => {
        expect(isHardcodedCssLength("26px")).toBe(true);
        expect(isHardcodedCssLength(" 1.5rem ")).toBe(true);
        expect(isHardcodedCssLength("0 0 32px")).toBe(true);
        expect(isHardcodedCssLength("0 0 auto")).toBe(false);
    });

    it("recognises dimension keys, custom properties, and React-px properties", () => {
        expect(isDimensionPropertyKey("width")).toBe(true);
        expect(isDimensionPropertyKey("flex")).toBe(true);
        expect(isDimensionPropertyKey("paddingInline")).toBe(true);
        expect(isDimensionPropertyKey("--switch-thumb-travel")).toBe(true);
        expect(isDimensionPropertyKey("color")).toBe(false);
        expect(REACT_PX_PROPERTY.test("height")).toBe(true);
        expect(REACT_PX_PROPERTY.test("flex")).toBe(false);
    });
});
