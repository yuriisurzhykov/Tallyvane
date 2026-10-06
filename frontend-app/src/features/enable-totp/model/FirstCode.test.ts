import { describe, expect, it } from "vitest";
import { FirstCode } from "./FirstCode";

describe("FirstCode", () => {
    it("keeps the digits, however they were grouped", () => {
        expect(new FirstCode("123 456").digits()).toBe("123456");
        expect(new FirstCode("123-456").digits()).toBe("123456");
    });

    it("is complete at six digits and not before or after", () => {
        expect(new FirstCode("12345").isComplete()).toBe(false);
        expect(new FirstCode("123456").isComplete()).toBe(true);
        expect(new FirstCode("1234567").isComplete()).toBe(false);
    });

    it("does not take letters for digits", () => {
        expect(new FirstCode("12a456").isComplete()).toBe(false);
    });
});
