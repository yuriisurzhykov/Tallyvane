import { describe, expect, it } from "vitest";
import { ActivityLog } from "./ActivityLog";
import { SecurityEntry } from "./SecurityEntry";

function entry(occurredAt: string): SecurityEntry {
    return new SecurityEntry({ kind: "signed_in", occurred_at: occurredAt, first_from_device: false });
}

function times(log: ActivityLog): string[] {
    return log.map((each) => each.occurredOn("en"));
}

describe("ActivityLog", () => {
    it("has nothing more when the server gave no cursor", () => {
        const log = new ActivityLog([entry("2026-10-06T10:00:00Z")], undefined);

        expect(log.hasMore()).toBe(false);
        expect(log.after()).toBeUndefined();
    });

    it("holds the cursor the server gave, untouched", () => {
        const log = new ActivityLog([entry("2026-10-06T10:00:00Z")], "41");

        expect(log.hasMore()).toBe(true);
        expect(log.after()).toBe("41");
    });

    it("puts the page that follows after what it has, and goes on from where that page ends", () => {
        const first = new ActivityLog([entry("2026-10-06T10:00:00Z")], "41");
        const second = new ActivityLog([entry("2026-10-05T10:00:00Z")], undefined);

        const both = first.andThen(second);

        expect(times(both)).toEqual([...times(first), ...times(second)]);
        expect(both.hasMore()).toBe(false);
    });

    it("leaves the log it was added to as it was", () => {
        const first = new ActivityLog([entry("2026-10-06T10:00:00Z")], "41");

        first.andThen(new ActivityLog([entry("2026-10-05T10:00:00Z")], undefined));

        expect(times(first)).toHaveLength(1);
        expect(first.after()).toBe("41");
    });

    it("knows it is empty", () => {
        expect(new ActivityLog([], undefined).isEmpty()).toBe(true);
        expect(new ActivityLog([entry("2026-10-06T10:00:00Z")], undefined).isEmpty()).toBe(false);
    });
});
