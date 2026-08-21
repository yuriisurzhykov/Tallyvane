// noinspection HtmlUnknownAttribute

import { Linter } from "eslint";
import { describe, expect, it } from "vitest";
import rule from "./no-raw-dimension-value";
import { onlyMessage } from "./test-helpers";

const linter = new Linter();

function lint(code: string) {
    return linter.verify(code, {
        languageOptions: { ecmaVersion: 2022, sourceType: "module", parserOptions: { ecmaFeatures: { jsx: true } } },
        plugins: { local: { rules: { "no-raw-dimension-value": rule } } },
        rules: { "local/no-raw-dimension-value": "error" },
    });
}

describe("no-raw-dimension-value", () => {
    it("flags a bare px/rem literal across several dimension-bearing style properties", () => {
        expect(lint('const x = <div style={{ width: "26px" }} />;')).toHaveLength(1);
        expect(lint('const x = <div style={{ marginTop: "1.5rem" }} />;')).toHaveLength(1);
        expect(lint('const x = <div style={{ fontSize: "18px" }} />;')).toHaveLength(1);
        expect(lint('const x = <div style={{ borderRadius: "6px" }} />;')).toHaveLength(1);
    });

    it("flags a raw dimension literal inside a template literal, with the exact property/value in the message", () => {
        const messages = lint("const x = <div style={{ gap: `26px` }} />;");
        expect(onlyMessage(messages)).toContain('"gap"');
        expect(onlyMessage(lint("const x = <div style={{ gap: `26px` }} />;"))).toContain("26px");
    });

    it("flags a dimension literal with surrounding whitespace, in both a plain string and a template literal", () => {
        expect(lint('const x = <div style={{ width: " 26px " }} />;')).toHaveLength(1);
        expect(lint("const x = <div style={{ width: ` 26px ` }} />;")).toHaveLength(1);
    });

    it("flags an unresolved identifier used as a dimension — fail-closed, not a named-constant exemption", () => {
        expect(lint("const x = <div style={{ width: someVariable }} />;")).toHaveLength(1);
        expect(onlyMessage(lint("const x = <div style={{ width: someVariable }} />;"))).toContain("hardcoded dimension");
    });

    it("does not flag a numeric literal 0, or a unitless zIndex", () => {
        expect(lint("const x = <div style={{ zIndex: 5, top: 0 }} />;")).toHaveLength(0);
    });

    it("flags a non-zero numeric literal on a React-px property (height: 32 is 32px)", () => {
        expect(lint("const x = <div style={{ height: 32 }} />;")).toHaveLength(1);
    });

    it("does not flag (and does not crash on) a computed, non-literal object key", () => {
        expect(lint('const x = <div style={{ [dynamicKey]: "26px" }} />;')).toHaveLength(0);
    });

    it("skips a spread element inside the style object without crashing, and still flags a real sibling property", () => {
        expect(lint('const x = <div style={{ ...base, width: "26px" }} />;')).toHaveLength(1);
    });

    it("ignores a style attribute whose value is a plain string literal, not an expression container", () => {
        expect(lint('const x = <div style="width: 26px" />;')).toHaveLength(0);
    });

    it("flags a bare dimension literal even when its property key is quoted (a Literal node, not an Identifier)", () => {
        expect(lint('const x = <div style={{ "width": "26px" }} />;')).toHaveLength(1);
    });

    it("does NOT flag a calc()/clamp()/var() expression", () => {
        expect(lint('const x = <div style={{ width: "calc(100% - 2rem)" }} />;')).toHaveLength(0);
        expect(lint('const x = <div style={{ width: "var(--ds-dimension-md)" }} />;')).toHaveLength(0);
        expect(lint('const x = <div style={{ flex: "0 0 var(--control-height-sm)" }} />;')).toHaveLength(0);
    });

    it("does NOT flag a ch-unit measure — character metrics, not a dimension-scale concern", () => {
        expect(lint('const x = <div style={{ width: "70ch" }} />;')).toHaveLength(0);
    });

    it("flags every percentage, including 100%/50% — no fill-parent/center exemption", () => {
        expect(lint('const x = <div style={{ width: "100%", height: "100%" }} />;')).toHaveLength(2);
        expect(lint('const x = <div style={{ top: "50%" }} />;')).toHaveLength(1);
        expect(lint('const x = <div style={{ width: "60%" }} />;')).toHaveLength(1);
    });

    it("does NOT flag a unitless line-height number", () => {
        expect(lint("const x = <div style={{ lineHeight: 1.5 }} />;")).toHaveLength(0);
    });

    it("flags a named constant holding a dimension string — the former official bypass", () => {
        expect(lint('const BOX_SIZE = "1.25rem"; const x = <div style={{ width: BOX_SIZE }} />;')).toHaveLength(1);
        expect(onlyMessage(lint('const H = "24rem"; const x = <div style={{ height: H }} />;'))).toContain("hardcoded dimension");
    });

    it("flags N + \"px\" and `${N}px` when N is a constant", () => {
        expect(lint('const N = 32; const x = <div style={{ width: N + "px" }} />;')).toHaveLength(1);
        expect(lint("const N = 32; const x = <div style={{ flex: `0 0 ${N}px` }} />;")).toHaveLength(1);
    });

    it("flags an imported identifier used as a dimension (fail-closed without following the other file)", () => {
        expect(lint('import { ROW_HEIGHT_PX } from "./constants"; const x = <div style={{ height: ROW_HEIGHT_PX }} />;')).toHaveLength(1);
    });

    it("flags mergeStyle's extra object and an extracted style object", () => {
        expect(lint('const BOX = "1.25rem"; const x = <div style={mergeStyle(style, { width: BOX })} />;')).toHaveLength(1);
        expect(lint('const wrapperStyle = { width: "100%" }; const x = <div style={wrapperStyle} />;')).toHaveLength(1);
    });

    it("flags flex/transform properties that carry a raw length", () => {
        expect(lint('const x = <div style={{ flex: "0 0 32px" }} />;')).toHaveLength(1);
        expect(lint('const x = <div style={{ transform: "translateY(8px)" }} />;')).toHaveLength(1);
    });

    it("does NOT flag a runtime interpolation (parameter or call) even with a unit suffix", () => {
        expect(lint("function Row({ start }) { return <div style={{ transform: `translateY(${start}px)` }} />; }")).toHaveLength(0);
        expect(lint("const x = <div style={{ height: meta.virtualizer.getTotalSize() }} />;")).toHaveLength(0);
        expect(lint("const x = <div style={{ flex: `${header.getSize()} 1 0%` }} />;")).toHaveLength(0);
        expect(lint("function Comp({ width }) { return <div style={{ width }} />; }")).toHaveLength(0);
        expect(lint('function Glyph({ open }) { return <div style={{ transform: open ? "rotate(180deg)" : undefined }} />; }')).toHaveLength(0);
    });

    it("does NOT flag a parameter-driven n + \"px\"", () => {
        expect(lint('function Comp({ n }) { return <div style={{ width: n + "px" }} />; }')).toHaveLength(0);
    });

    it("flags a viewport-unit literal too — no unit-based exemption", () => {
        expect(lint('const x = <div style={{ minHeight: "60vh" }} />;')).toHaveLength(1);
    });

    it("does not flag a non-dimension property with a color value", () => {
        expect(lint('const x = <div style={{ backgroundColor: "#fff" }} />;')).toHaveLength(0);
    });

    it("ignores a non-style attribute and a non-object style expression that is runtime", () => {
        expect(lint('const x = <div data-width="26px" />;')).toHaveLength(0);
        expect(lint("function Comp({ someStyleObject }) { return <div style={someStyleObject} />; }")).toHaveLength(0);
    });

    it("silences a finding only with a complete @architecture-exception (rule + adr)", () => {
        const silenced = lint(`
            function Comp() {
                // @architecture-exception rule=no-raw-dimension-value adr=ADR-042
                //   reason=virtualizer estimate cannot read a CSS variable
                return <div style={{ height: 32 }} />;
            }
        `);
        expect(silenced).toHaveLength(0);
        expect(lint(`
            function Comp() {
                // @architecture-exception rule=no-raw-dimension-value
                return <div style={{ height: 32 }} />;
            }
        `)).toHaveLength(1);
        expect(lint(`
            function Comp() {
                // @architecture-exception rule=no-raw-color-value adr=ADR-042
                return <div style={{ height: 32 }} />;
            }
        `)).toHaveLength(1);
    });
});
