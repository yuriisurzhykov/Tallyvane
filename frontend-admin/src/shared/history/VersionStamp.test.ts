import { describe, expect, it } from "vitest";
import { VersionStamp, type AuthorWords } from "./VersionStamp";

const words: AuthorWords = { you: "You", migration: "Migration", account: (id) => `Account ${id}` };
const ACCOUNT = "3f2a9c1e-7d44-4b0a-9f55-0a1b2c3d4e5f";

describe("VersionStamp", () => {
    it("calls the person looking 'you' and anyone else by the start of their account id", () => {
        const stamp = new VersionStamp(ACCOUNT, "2026-09-30T20:11:00Z", "Tighten");

        expect(stamp.author(words, { is: (id) => id === ACCOUNT })).toBe("You");
        expect(stamp.author(words, { is: () => false })).toBe("Account 3f2a9c1e");
    });

    it("says a migration made what no account made", () => {
        expect(new VersionStamp(undefined, "2026-09-30T20:11:00Z", undefined).author(words, { is: () => true })).toBe("Migration");
    });

    it("keeps the reason, and has none for what a migration did", () => {
        expect(new VersionStamp(ACCOUNT, "2026-09-30T20:11:00Z", "Tighten").reason()).toBe("Tighten");
        expect(new VersionStamp(undefined, "2026-09-30T20:11:00Z", undefined).reason()).toBeUndefined();
    });

    it("writes the time in the locale it is asked for", () => {
        const text = new VersionStamp(undefined, "2026-09-30T20:11:00Z", undefined).when("en");

        expect(text).toContain("2026");
    });
});
