import { describe, expect, it } from "vitest";
import type { components } from "frontend-shared/api";
import { PolicyHistory } from "./PolicyHistory";
import { PolicyConsequences } from "./PolicyConsequences";
import { PolicyVersion } from "./PolicyVersion";

type Wire = components["schemas"]["PolicyVersion"];

const wire = (number: number, extra: Partial<Wire> = {}): Wire => ({
    number,
    made_at: "2026-09-30T20:11:00Z",
    steps: [{ accepts: ["google"], necessity: "always" }],
    attempt_lifetime_millis: 600_000,
    max_failures: 5,
    first_delay_millis: 2_000,
    ...extra,
});

describe("PolicyVersion", () => {
    it("is in force when the server says so and not otherwise", () => {
        expect(new PolicyVersion(wire(2, { in_force: { since: "2026-10-01T00:00:00Z" } })).isInForce()).toBe(true);
        expect(new PolicyVersion(wire(1)).isInForce()).toBe(false);
    });

    it("tells who put it in force and why only while it is", () => {
        const now = new PolicyVersion(wire(2, { in_force: { since: "2026-10-01T00:00:00Z", reason: "Spike" } }));

        expect(now.inForce()?.reason()).toBe("Spike");
        expect(new PolicyVersion(wire(1)).inForce()).toBeUndefined();
    });

    it("keeps the reason it was made for, and has none when a migration made it", () => {
        expect(new PolicyVersion(wire(2, { comment: "Tighten" })).made().reason()).toBe("Tighten");
        expect(new PolicyVersion(wire(1)).made().reason()).toBeUndefined();
    });

    it("starts the next version as a copy that sends exactly what this one held", () => {
        const request = new PolicyVersion(wire(3)).asForm().toRequest("login", "Again");

        expect(request).toMatchObject({
            steps: [{ accepts: ["google"], necessity: "always" }],
            attempt_lifetime_millis: 600_000,
            max_failures: 5,
            first_delay_millis: 2_000,
        });
    });
});

describe("PolicyHistory", () => {
    it("starts a new version from the one in force, wherever it is in the list", () => {
        const history = new PolicyHistory([
            new PolicyVersion(wire(3)),
            new PolicyVersion(wire(2, { in_force: { since: "2026-10-01T00:00:00Z" } })),
            new PolicyVersion(wire(1)),
        ]);

        expect(history.startingPoint()?.number()).toBe(2);
    });

    it("starts from the newest when none is marked, and from nothing when there are none", () => {
        expect(new PolicyHistory([new PolicyVersion(wire(2)), new PolicyVersion(wire(1))]).startingPoint()?.number()).toBe(2);
        expect(new PolicyHistory([]).startingPoint()).toBeUndefined();
        expect(new PolicyHistory([]).isEmpty()).toBe(true);
    });

    it("shows its versions in the order it was given them", () => {
        const history = new PolicyHistory([new PolicyVersion(wire(2)), new PolicyVersion(wire(1))]);

        expect(history.map((version) => version.number())).toEqual([2, 1]);
    });
});

describe("PolicyConsequences", () => {
    const words = { people: (n: number) => `${String(n)} people`, cannotPass: (n: number) => `${String(n)} stuck` };

    it("says how many people use the purpose, and only then who would be stuck", () => {
        expect(new PolicyConsequences({ people: 312, cannot_pass: 0 }).describe(words)).toEqual(["312 people"]);
        expect(new PolicyConsequences({ people: 312, cannot_pass: 4 }).describe(words)).toEqual(["312 people", "4 stuck"]);
    });

    it("is harmless when nobody would be stuck", () => {
        expect(new PolicyConsequences({ people: 3, cannot_pass: 0 }).isHarmless()).toBe(true);
        expect(new PolicyConsequences({ people: 3, cannot_pass: 1 }).isHarmless()).toBe(false);
    });
});
