import { describe, expect, it } from "vitest";
import { Diff } from "./Diff";
import { DiffLine, type DiffLook } from "./DiffLine";

const look: DiffLook<string> = {
    section: (text) => `# ${text}`,
    removed: (text) => `- ${text}`,
    added: (text) => `+ ${text}`,
};

const drawn = (diff: Diff): string[] => diff.map((line) => line.appearAs(look));

describe("DiffLine", () => {
    it("is drawn as the kind it is, with the text it was given", () => {
        expect(DiffLine.section("limits").appearAs(look)).toBe("# limits");
        expect(DiffLine.removed("5 wrong answers").appearAs(look)).toBe("- 5 wrong answers");
        expect(DiffLine.added("3 wrong answers").appearAs(look)).toBe("+ 3 wrong answers");
    });
});

describe("Diff", () => {
    it("is empty when there is nothing to tell", () => {
        expect(Diff.none().isEmpty()).toBe(true);
        expect(Diff.of([]).isEmpty()).toBe(true);
    });

    it("puts a heading over the lines of a part", () => {
        const diff = Diff.section("limits", [DiffLine.removed("5"), DiffLine.added("3")]);

        expect(drawn(diff)).toEqual(["# limits", "- 5", "+ 3"]);
    });

    it("has no heading for a part with no lines", () => {
        expect(Diff.section("limits", []).isEmpty()).toBe(true);
    });

    it("joins two in order and leaves both as they were", () => {
        const steps = Diff.section("steps", [DiffLine.added("step 2")]);
        const limits = Diff.section("limits", [DiffLine.removed("5")]);

        const both = steps.and(limits);

        expect(drawn(both)).toEqual(["# steps", "+ step 2", "# limits", "- 5"]);
        expect(drawn(steps)).toEqual(["# steps", "+ step 2"]);
        expect(drawn(limits)).toEqual(["# limits", "- 5"]);
    });
});
