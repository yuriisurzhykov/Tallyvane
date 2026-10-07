import { describe, expect, it } from "vitest";
import type { components } from "frontend-shared/api";
import { SecurityEntry, type EntryKind, type EntryWords } from "./SecurityEntry";

type Wire = components["schemas"]["SecurityEntry"];

const WORDS: EntryWords = {
    titles: {
        signed_in: "Signed in",
        totp_turned_on: "Two-step sign-in turned on",
        totp_turned_off: "Two-step sign-in turned off",
        recovery_code_spent: "Recovery code used to sign in",
        recovery_codes_reissued: "New recovery codes issued",
        other_devices_signed_out: "Signed out on all other devices",
        guessing_stopped: "Wrong codes were entered and the attempt was closed",
    },
    withCodesLeft: (title, count) => `${title} · ${String(count)} left`,
    named: (name, device) => `${name} · ${device}`,
    newDevice: "New device",
    securityChange: "Security change",
    stopped: "Stopped",
    device: {
        unknownDevice: "Unknown device",
        otherBrowser: "Other browser",
        unknownSystem: "unknown system",
        mobile: "mobile",
        described: (browser, system) => `${browser} on ${system}`,
    },
};

function entry(kind: EntryKind, overrides: Partial<Wire> = {}): SecurityEntry {
    return new SecurityEntry({ kind, occurred_at: "2026-10-06T14:32:00Z", first_from_device: false, ...overrides });
}

describe("SecurityEntry tone and badge", () => {
    it.each<[EntryKind, string, string | undefined]>([
        ["signed_in", "neutral", undefined],
        ["totp_turned_on", "neutral", undefined],
        ["recovery_codes_reissued", "neutral", undefined],
        ["totp_turned_off", "attention", "Security change"],
        ["recovery_code_spent", "attention", "Security change"],
        ["other_devices_signed_out", "attention", "Security change"],
        ["guessing_stopped", "danger", "Stopped"],
    ])("marks %s as %s with %s", (kind, tone, badge) => {
        expect(entry(kind).tone()).toBe(tone);
        expect(entry(kind).badge(WORDS)).toBe(badge);
    });

    it("marks a first sign-in from a device as worth a look", () => {
        const first = entry("signed_in", { first_from_device: true });

        expect(first.tone()).toBe("attention");
        expect(first.badge(WORDS)).toBe("New device");
    });
});

describe("SecurityEntry.title", () => {
    it("names what happened", () => {
        expect(entry("totp_turned_off").title(WORDS)).toBe("Two-step sign-in turned off");
    });

    it("says how many recovery codes are left when the server does", () => {
        expect(entry("recovery_code_spent", { codes_left: 3 }).title(WORDS)).toBe("Recovery code used to sign in · 3 left");
        expect(entry("recovery_code_spent", { codes_left: 0 }).title(WORDS)).toBe("Recovery code used to sign in · 0 left");
    });
});

describe("SecurityEntry.device", () => {
    it("is the device as it was, with the name the person had given it", () => {
        const named = entry("signed_in", { device: { browser: "chrome", platform: "windows", mobile: false, name: "Work laptop" } });

        expect(named.device(WORDS)).toBe("Work laptop · Chrome on Windows");
    });

    it("is only the description when the device had no name", () => {
        const unnamed = entry("signed_in", { device: { browser: "safari", platform: "ios", mobile: true } });

        expect(unnamed.device(WORDS)).toBe("Safari on iOS · mobile");
    });

    it("is nothing when no device is known", () => {
        expect(entry("guessing_stopped").device(WORDS)).toBeUndefined();
    });
});

describe("SecurityEntry.occurredOn", () => {
    it("is the date and the time, in the zone of the reader", () => {
        const text = entry("signed_in").occurredOn("en");

        expect(text).toContain("2026");
        expect(text).toMatch(/\d{1,2}:\d{2}/);
    });
});
