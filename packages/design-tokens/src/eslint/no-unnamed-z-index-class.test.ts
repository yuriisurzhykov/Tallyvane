import { describe, expect, it } from "vitest";
import { Linter } from "eslint";
import rule from "./no-unnamed-z-index-class";
import { onlyMessage } from "./test-helpers";

const linter = new Linter();

function lint(code: string) {
    return linter.verify(code, {
        plugins: { local: { rules: { "no-unnamed-z-index-class": rule } } },
        languageOptions: {
            ecmaVersion: "latest",
            sourceType: "module",
            parserOptions: { ecmaFeatures: { jsx: true } },
        },
        rules: { "local/no-unnamed-z-index-class": "error" },
    });
}

describe("no-unnamed-z-index-class", () => {
    it("flags a numeric stacking utility", () => {
        const message = onlyMessage(lint('const x = <div className="z-50" />;'));
        expect(message).toContain("z-50");
    });

    it("flags an arbitrary stacking utility, which is the escalation the rule exists to stop", () => {
        expect(lint('const x = <div className="z-[9999]" />;')).toHaveLength(1);
    });

    it("flags a negative layer — still a layer, still ordered against everything else", () => {
        expect(lint('const x = <div className="-z-10" />;')).toHaveLength(1);
    });

    it("flags through a responsive modifier", () => {
        expect(lint('const x = <div className="lg:z-40" />;')).toHaveLength(1);
    });

    it("leaves a named layer alone — that is the whole point of the scale", () => {
        expect(lint('const x = <div className="z-modal" />;')).toHaveLength(0);
        expect(lint('const x = <div className="hover:z-tooltip" />;')).toHaveLength(0);
    });

    // `z-auto` opts out of stacking rather than choosing a position in it.
    it("leaves z-auto alone", () => {
        expect(lint('const x = <div className="z-auto" />;')).toHaveLength(0);
    });

    it("does not mistake another utility that merely ends in a number", () => {
        expect(lint('const x = <div className="gap-2 grid-cols-12 elevation1" />;')).toHaveLength(0);
    });

    // The className walker handles conditionals and helper calls, so the rule
    // must not be escapable by wrapping the string in one.
    it("sees through a conditional inside a cn() call", () => {
        expect(lint('const x = <div className={cn(open ? "z-50" : "z-modal")} />;')).toHaveLength(1);
    });
});
