import { describe, expect, it } from "vitest";
import { Span, type SpanWords } from "./Span";

const words: SpanWords = {
    seconds: (n) => `${String(n)} s`,
    minutes: (n) => `${String(n)} min`,
    hours: (n) => `${String(n)} h`,
    days: (n) => `${String(n)} d`,
};

describe("Span", () => {
    it("counts in milliseconds whatever it was made from", () => {
        expect(Span.of(2, "minutes").toMillis()).toBe(120_000);
        expect(Span.of(1, "days").toMillis()).toBe(86_400_000);
        expect(Span.ofMillis(1500).toMillis()).toBe(1500);
    });

    it("gives its length in any unit, as a fraction when it does not divide evenly", () => {
        expect(Span.of(90, "minutes").amountIn("hours")).toBe(1.5);
        expect(Span.of(7, "days").amountIn("minutes")).toBe(10_080);
    });

    it("is said in the largest unit that divides it evenly", () => {
        expect(Span.of(24, "hours").naturalUnit()).toBe("days");
        expect(Span.of(90, "minutes").naturalUnit()).toBe("minutes");
        expect(Span.of(30, "seconds").naturalUnit()).toBe("seconds");
    });

    it("keeps to the units on offer, falling back to the smallest of them", () => {
        expect(Span.of(2, "days").naturalUnit(["minutes", "hours"])).toBe("hours");
        expect(Span.of(1500, "seconds").naturalUnit(["minutes", "hours"])).toBe("minutes");
        expect(() => Span.of(1, "hours").naturalUnit([])).toThrow();
    });

    it("describes itself in the words it is given", () => {
        expect(Span.of(7, "days").describe(words)).toBe("7 d");
        expect(Span.of(36, "hours").describe(words)).toBe("36 h");
        expect(Span.of(2, "seconds").describe(words)).toBe("2 s");
    });

    it("is equal to another span of the same length", () => {
        expect(Span.of(60, "minutes").equals(Span.of(1, "hours"))).toBe(true);
        expect(Span.of(60, "minutes").equals(Span.of(2, "hours"))).toBe(false);
    });
});
