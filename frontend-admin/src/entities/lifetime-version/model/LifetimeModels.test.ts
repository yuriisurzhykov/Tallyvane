import { describe, expect, it } from "vitest";
import type { components } from "frontend-shared/api";
import type { DiffLook } from "@/shared/diff";
import { Span, type SpanWords } from "@/shared/span";
import { LifetimeConsequences } from "./LifetimeConsequences";
import { LifetimeFaults, LIFETIME_LIMIT_FIELDS, type LifetimeFaultWords } from "./LifetimeFaults";
import { type LifetimeWords } from "./LifetimeForm";
import { LifetimeHistory } from "./LifetimeHistory";
import { LifetimeVersion } from "./LifetimeVersion";

type Wire = components["schemas"]["LifetimeVersion"];

const spanWords: SpanWords = {
    seconds: (n) => `${String(n)} s`,
    minutes: (n) => `${String(n)} min`,
    hours: (n) => `${String(n)} h`,
    days: (n) => `${String(n)} d`,
};
const words: LifetimeWords = {
    span: spanWords,
    summary: (idle, absolute, freshness) => `idle ${idle}, absolute ${absolute}, fresh ${freshness}`,
    idleLine: (idle) => `unused ${idle}`,
    absoluteLine: (absolute) => `lives ${absolute}`,
    freshnessLine: (freshness) => `fresh ${freshness}`,
};
const look: DiffLook<string> = { section: (t) => `# ${t}`, removed: (t) => `- ${t}`, added: (t) => `+ ${t}` };

const wire = (number: number, extra: Partial<Wire> = {}): Wire => ({
    number,
    made_at: "2026-09-30T20:11:00Z",
    idle_millis: 86_400_000,
    absolute_millis: 604_800_000,
    freshness_millis: 300_000,
    ...extra,
});

describe("LifetimeForm", () => {
    const form = () => new LifetimeVersion(wire(1)).asForm();

    it("says the three lengths in one line, each in the unit it is most naturally said in", () => {
        expect(form().summary(words)).toBe("idle 1 d, absolute 7 d, fresh 5 min");
    });

    it("sends the lengths in milliseconds", () => {
        expect(form().toRequest("browser", "Why")).toEqual({
            client: "browser",
            idle_millis: 86_400_000,
            absolute_millis: 604_800_000,
            freshness_millis: 300_000,
            comment: "Why",
        });
    });

    it("changes one length and leaves the form it was asked to change alone", () => {
        const before = form();

        const changed = before.withIdle(Span.of(12, "hours")).withAbsolute(Span.of(3, "days")).withFreshness(Span.of(2, "minutes"));

        expect(changed.toRequest("admin", "x")).toMatchObject({ idle_millis: 43_200_000, absolute_millis: 259_200_000, freshness_millis: 120_000 });
        expect(before.toRequest("admin", "x")).toMatchObject({ idle_millis: 86_400_000 });
    });
});

describe("LifetimeVersion", () => {
    it("is in force when the server says so, and then says by whom and why", () => {
        const now = new LifetimeVersion(wire(2, { in_force: { since: "2026-10-01T00:00:00Z", reason: "Spike" } }));

        expect(now.isInForce()).toBe(true);
        expect(now.inForce()?.reason()).toBe("Spike");
        expect(new LifetimeVersion(wire(1)).isInForce()).toBe(false);
        expect(new LifetimeVersion(wire(1)).inForce()).toBeUndefined();
    });
});

describe("LifetimeHistory", () => {
    it("starts a new version from the one in force, else from the newest, else from nothing", () => {
        const inForce = wire(1, { in_force: { since: "2026-10-01T00:00:00Z" } });

        expect(new LifetimeHistory([new LifetimeVersion(wire(2)), new LifetimeVersion(inForce)]).startingPoint()?.number()).toBe(1);
        expect(new LifetimeHistory([new LifetimeVersion(wire(2)), new LifetimeVersion(wire(1))]).startingPoint()?.number()).toBe(2);
        expect(new LifetimeHistory([]).startingPoint()).toBeUndefined();
    });
});

describe("LifetimeConsequences", () => {
    const text = { sessions: (n: number) => `${String(n)} live`, wouldEnd: (n: number) => `${String(n)} end` };

    it("names the sessions that would end only when some would", () => {
        expect(new LifetimeConsequences({ sessions: 10, would_end: 0 }).describe(text)).toEqual(["10 live"]);
        expect(new LifetimeConsequences({ sessions: 10, would_end: 4 }).describe(text)).toEqual(["10 live", "4 end"]);
        expect(new LifetimeConsequences({ sessions: 10, would_end: 0 }).isHarmless()).toBe(true);
        expect(new LifetimeConsequences({ sessions: 10, would_end: 1 }).isHarmless()).toBe(false);
    });
});

describe("LifetimeFaults", () => {
    const faultWords: LifetimeFaultWords = {
        unknown: "unknown",
        outOfBounds: (limit) => `${limit} out`,
        belowIdle: "below idle",
        commentInvalid: "comment",
        unmapped: (field, code) => `${field}/${code}`,
    };
    const faults = new LifetimeFaults(faultWords);

    it("words each fault the server names for lifetimes", () => {
        expect(faults.phrase({ field: "idle_millis", code: "out-of-bounds" })).toBe("idle out");
        expect(faults.phrase({ field: "absolute_millis", code: "out-of-bounds" })).toBe("absolute out");
        expect(faults.phrase({ field: "freshness_millis", code: "out-of-bounds" })).toBe("freshness out");
        expect(faults.phrase({ field: "absolute_millis", code: "below-idle" })).toBe("below idle");
        expect(faults.phrase({ field: "comment", code: "comment-invalid" })).toBe("comment");
        expect(faults.phrase({ field: "client", code: "unknown" })).toBe("unknown");
        expect(faults.phrase({ field: "idle_millis", code: "new-rule" })).toBe("idle_millis/new-rule");
    });

    it("lists the fields that show their error beside the input", () => {
        expect(LIFETIME_LIMIT_FIELDS).toEqual(["idle_millis", "absolute_millis", "freshness_millis"]);
    });
});

describe("LifetimeVersion.changesFrom", () => {
    const told = (now: LifetimeVersion, inForce: LifetimeVersion) => now.changesFrom(inForce, words).map((line) => line.appearAs(look));

    it("has nothing to tell about two versions that hold the same lengths", () => {
        expect(new LifetimeVersion(wire(2)).changesFrom(new LifetimeVersion(wire(1)), words).isEmpty()).toBe(true);
    });

    it("tells each length that changed, what it was in force and what it would be, and no other", () => {
        const now = new LifetimeVersion(wire(2, { idle_millis: 43_200_000, freshness_millis: 180_000 }));

        expect(told(now, new LifetimeVersion(wire(1)))).toEqual([
            "- unused 1 d",
            "+ unused 12 h",
            "- fresh 5 min",
            "+ fresh 3 min",
        ]);
    });
});
