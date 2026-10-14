import { describe, expect, it } from "vitest";
import { formatCount } from "./formatCount";

describe("formatCount", () => {
    it("groups thousands", () => {
        expect(formatCount(1204)).toBe("1,204");
        expect(formatCount(312)).toBe("312");
    });
});
