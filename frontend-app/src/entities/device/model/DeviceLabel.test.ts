import { describe, expect, it } from "vitest";
import { DeviceLabel, type DeviceWords } from "./DeviceLabel";

const WORDS: DeviceWords = {
    unknownDevice: "Unknown device",
    otherBrowser: "Other browser",
    unknownSystem: "unknown system",
    mobile: "mobile",
    described: (browser, system) => `${browser} on ${system}`,
};

describe("DeviceLabel.describe", () => {
    it.each<[ConstructorParameters<typeof DeviceLabel>, string]>([
        [["chrome", "windows", false], "Chrome on Windows"],
        [["safari", "ios", true], "Safari on iOS · mobile"],
        [["edge", "macos", false], "Edge on macOS"],
        [["firefox", "linux", false], "Firefox on Linux"],
        [["opera", "chromeos", false], "Opera on ChromeOS"],
        [["other", "android", false], "Other browser on Android"],
        [["chrome", "other", false], "Chrome on unknown system"],
        [["other", "other", false], "Unknown device"],
        [["other", "other", true], "Unknown device · mobile"],
    ])("calls %j %s", (parts, expected) => {
        expect(new DeviceLabel(...parts).describe(WORDS)).toBe(expected);
    });
});
