import { describe, expect, it } from "vitest";
import type { components } from "frontend-shared/api";
import { Device } from "./Device";
import type { DeviceWords } from "./DeviceLabel";

type Wire = components["schemas"]["Device"];

const WORDS: DeviceWords = {
    unknownDevice: "Unknown device",
    otherBrowser: "Other browser",
    unknownSystem: "unknown system",
    mobile: "mobile",
    described: (browser, system) => `${browser} on ${system}`,
};

const NOW = new Date("2026-10-03T12:00:00Z");

function device(overrides: Partial<Wire> = {}): Device {
    return new Device({
        id: "7a0b3e0e-0000-4000-8000-000000000001",
        browser: "chrome",
        platform: "windows",
        mobile: false,
        signed_in_at: "2026-10-01T08:00:00Z",
        last_active_at: "2026-10-03T11:59:00Z",
        current: false,
        ...overrides,
    });
}

function agoFrom(lastActiveAt: string): string {
    return device({ last_active_at: lastActiveAt }).lastActiveAgo(NOW, "en");
}

describe("Device.description", () => {
    it("names the device the way a label does, mobile included", () => {
        expect(device({ browser: "safari", platform: "ios", mobile: true }).description(WORDS)).toBe("Safari on iOS · mobile");
    });
});

describe("Device.givenName", () => {
    it("is what the person called it", () => {
        expect(device({ name: "Work laptop" }).givenName()).toBe("Work laptop");
    });

    it("is empty until they do", () => {
        expect(device().givenName()).toBe("");
    });
});

describe("Device activity", () => {
    it("is active now for a minute or so, which is as fine as the server keeps it", () => {
        expect(device({ last_active_at: "2026-10-03T11:59:00Z" }).isActiveAt(NOW)).toBe(true);
        expect(device({ last_active_at: "2026-10-03T11:58:01Z" }).isActiveAt(NOW)).toBe(true);
    });

    it("is not active now from two minutes on", () => {
        expect(device({ last_active_at: "2026-10-03T11:58:00Z" }).isActiveAt(NOW)).toBe(false);
    });

    it("is active now when the clock of the server is ahead of this one", () => {
        expect(device({ last_active_at: "2026-10-03T12:00:30Z" }).isActiveAt(NOW)).toBe(true);
    });

    it("says minutes, then hours, then days", () => {
        expect(agoFrom("2026-10-03T11:55:00Z")).toBe("5 minutes ago");
        expect(agoFrom("2026-10-03T11:01:00Z")).toBe("59 minutes ago");
        expect(agoFrom("2026-10-03T11:00:00Z")).toBe("1 hour ago");
        expect(agoFrom("2026-10-03T09:00:00Z")).toBe("3 hours ago");
        expect(agoFrom("2026-10-02T12:00:00Z")).toBe("yesterday");
        expect(agoFrom("2026-09-27T12:00:00Z")).toBe("6 days ago");
    });

    it("never says a time in the future", () => {
        expect(agoFrom("2026-10-03T12:05:00Z")).toBe("this minute");
    });
});

describe("Device identity", () => {
    it("is the session the server named", () => {
        expect(device().key()).toBe("7a0b3e0e-0000-4000-8000-000000000001");
    });

    it("knows whether it is the one asking", () => {
        expect(device({ current: true }).isCurrent()).toBe(true);
        expect(device().isCurrent()).toBe(false);
    });
});
