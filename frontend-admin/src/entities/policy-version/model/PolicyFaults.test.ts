import { describe, expect, it } from "vitest";
import { PolicyFaults, POLICY_LIMIT_FIELDS, type PolicyFaultWords } from "./PolicyFaults";

const words: PolicyFaultWords = {
    unknown: "unknown",
    stepEmpty: (position) => `step ${String(position)} is empty`,
    nothingIdentifies: "nothing identifies",
    belowTheFloor: "below the floor",
    wouldLockPeopleOut: "would lock people out",
    outOfBounds: (limit) => `${limit} is out of bounds`,
    commentInvalid: "comment invalid",
    unmapped: (field, code) => `${field}/${code}`,
};

const faults = new PolicyFaults(words);

describe("PolicyFaults", () => {
    it("numbers the empty step from 1, as the screen does, though the server counts from 0", () => {
        expect(faults.phrase({ field: "steps[0].accepts", code: "empty" })).toBe("step 1 is empty");
        expect(faults.phrase({ field: "steps[2].accepts", code: "empty" })).toBe("step 3 is empty");
    });

    it("words each way the steps as a whole can be refused", () => {
        expect(faults.phrase({ field: "steps", code: "nothing-identifies-the-account" })).toBe("nothing identifies");
        expect(faults.phrase({ field: "steps", code: "below-the-floor" })).toBe("below the floor");
        expect(faults.phrase({ field: "steps", code: "would-lock-people-out" })).toBe("would lock people out");
    });

    it("names the limit that is out of bounds by the form's own name for it", () => {
        expect(faults.phrase({ field: "attempt_lifetime_millis", code: "out-of-bounds" })).toBe("attemptLifetime is out of bounds");
        expect(faults.phrase({ field: "max_failures", code: "out-of-bounds" })).toBe("maxFailures is out of bounds");
        expect(faults.phrase({ field: "first_delay_millis", code: "out-of-bounds" })).toBe("firstDelay is out of bounds");
    });

    it("words a bad comment and an unknown purpose", () => {
        expect(faults.phrase({ field: "comment", code: "comment-invalid" })).toBe("comment invalid");
        expect(faults.phrase({ field: "purpose", code: "unknown" })).toBe("unknown");
    });

    it("still says something for a fault it has no words for, so none goes unseen", () => {
        expect(faults.phrase({ field: "steps", code: "new-rule" })).toBe("steps/new-rule");
    });

    it("lists the fields that show their error beside the input", () => {
        expect(POLICY_LIMIT_FIELDS).toEqual(["attempt_lifetime_millis", "max_failures", "first_delay_millis"]);
    });
});
