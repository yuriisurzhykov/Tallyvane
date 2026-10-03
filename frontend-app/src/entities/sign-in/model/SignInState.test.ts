import { describe, expect, it } from "vitest";
import { SignInState, type SignInRoute } from "./SignInState";

const NAMED: SignInRoute<string> = {
    answer: (offer) => `answer:${offer.firstKind()}:${String(offer.pause())}:${String(offer.offers("recovery_code"))}`,
    open: () => "open",
    startAgain: () => "again",
    blocked: () => "blocked",
};

const stateOf = (state: "awaiting" | "paused" | "complete" | "restricted" | "exhausted" | "expired", factors: ("totp" | "recovery_code")[] = [], retryAfter?: number) =>
    new SignInState({ state, factors, ...(retryAfter !== undefined ? { retry_after: retryAfter } : {}) });

describe("SignInState", () => {
    it("asks for a code while the attempt waits, with the app's code first", () => {
        expect(stateOf("awaiting", ["totp", "recovery_code"]).proceed(NAMED)).toBe("answer:totp:0:true");
    });

    it("asks for the recovery code alone when that is all the account can pass", () => {
        expect(stateOf("awaiting", ["recovery_code"]).proceed(NAMED)).toBe("answer:recovery_code:0:true");
    });

    it("still asks while paused, and says for how long", () => {
        expect(stateOf("paused", ["totp"], 12).proceed(NAMED)).toBe("answer:totp:12:false");
    });

    it("opens when everything wanted is proved", () => {
        expect(stateOf("complete").proceed(NAMED)).toBe("open");
    });

    it("starts again when the attempt ran out or its last try was spent", () => {
        expect(stateOf("exhausted").proceed(NAMED)).toBe("again");
        expect(stateOf("expired").proceed(NAMED)).toBe("again");
    });

    it("does not let a restricted sign-in in", () => {
        expect(stateOf("restricted").proceed(NAMED)).toBe("blocked");
    });

    it("starts again rather than show a form nothing can be typed into", () => {
        expect(stateOf("awaiting", []).proceed(NAMED)).toBe("again");
    });
});
