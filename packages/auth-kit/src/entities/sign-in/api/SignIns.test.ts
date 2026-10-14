import { describe, expect, it } from "vitest";
import { createApi } from "frontend-shared/api";
import type { CodeReaction } from "../model/CodeAnswer";
import { SignIns } from "./SignIns";

interface Sent {
    readonly method: string;
    readonly url: string;
    readonly body: string | undefined;
}

/** The whole client chain over a fake network, so the paths, keys and failures are the real ones. */
function over(...answers: Response[]): { signIns: SignIns; sent: Sent[] } {
    const sent: Sent[] = [];
    const fetcher: typeof fetch = (input, init) => {
        sent.push({
            method: init?.method ?? "GET",
            url: typeof input === "string" ? input : input instanceof URL ? input.href : input.url,
            body: typeof init?.body === "string" ? init.body : undefined,
        });
        return Promise.resolve(answers.shift() ?? new Response(null, { status: 204 }));
    };
    return { signIns: new SignIns(createApi({ origin: "", fetcher })), sent };
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function problem(kind: string, status: number, headers: Record<string, string> = {}, errors?: unknown): Response {
    return new Response(JSON.stringify({ type: `https://tallyvane.com/errors/${kind}`, title: kind, status, ...(errors ? { errors } : {}) }), {
        status,
        headers: { "content-type": "application/problem+json", ...headers },
    });
}

/** Says which reaction a page would have been given, with what. */
const NAMED: CodeReaction<string> = {
    accepted: (left) => `accepted:${String(left)}`,
    wrong: (seconds) => `wrong:${String(seconds)}`,
    paused: (seconds) => `paused:${String(seconds)}`,
    over: () => "over",
    changed: () => "changed",
};

describe("SignIns.state", () => {
    it("reads where the attempt stands", async () => {
        const { signIns, sent } = over(json({ state: "awaiting", factors: ["totp", "recovery_code"] }));

        const state = await signIns.state();

        expect(sent[0]).toMatchObject({ method: "GET", url: "/api/v1/sign-in" });
        expect(state?.proceed({ answer: (offer) => `answer:${offer.firstKind()}`, open: () => "open", startAgain: () => "again", blocked: () => "blocked" })).toBe("answer:totp");
    });

    it("says there is no attempt for a 404, and fails for anything else", async () => {
        expect(await over(problem("not-found", 404)).signIns.state()).toBeUndefined();
        await expect(over(problem("internal", 500)).signIns.state()).rejects.toThrow();
    });
});

describe("SignIns.answer", () => {
    it("sends the kind and the code", async () => {
        const { signIns, sent } = over();

        await signIns.answer("totp", "123456");

        expect(sent[0]).toMatchObject({ method: "POST", url: "/api/v1/second-factor-codes", body: JSON.stringify({ kind: "totp", code: "123456" }) });
    });

    it("accepts a code from the app (204) with nothing left to tell", async () => {
        const answer = await over(new Response(null, { status: 204 })).signIns.answer("totp", "123456");

        expect(answer.when(NAMED)).toBe("accepted:undefined");
    });

    it("accepts a recovery code (200) and says how many are left", async () => {
        const answer = await over(json({ recovery_codes_remaining: 9 })).signIns.answer("recovery_code", "ABCDEFGHJK");

        expect(answer.when(NAMED)).toBe("accepted:9");
    });

    it("answers a wrong code with the pause that now applies, or none", async () => {
        const wrong = [{ field: "code", code: "wrong-code" }];

        expect((await over(problem("validation-failed", 422, { "retry-after": "4" }, wrong)).signIns.answer("totp", "000000")).when(NAMED)).toBe("wrong:4");
        expect((await over(problem("validation-failed", 422, {}, wrong)).signIns.answer("totp", "000000")).when(NAMED)).toBe("wrong:0");
    });

    it("answers a running pause (429) with what is left of it", async () => {
        const answer = await over(problem("slow-down", 429, { "retry-after": "30" })).signIns.answer("totp", "123456");

        expect(answer.when(NAMED)).toBe("paused:30");
    });

    it("answers a finished attempt (410) and a missing one (404) as over", async () => {
        expect((await over(problem("gone", 410)).signIns.answer("totp", "123456")).when(NAMED)).toBe("over");
        expect((await over(problem("not-found", 404)).signIns.answer("totp", "123456")).when(NAMED)).toBe("over");
    });

    it("answers an attempt that changed (409) as changed", async () => {
        const answer = await over(problem("conflict", 409)).signIns.answer("totp", "123456");

        expect(answer.when(NAMED)).toBe("changed");
    });

    it("fails for what is not an answer: an unknown kind, a broken server, a network that is down", async () => {
        await expect(over(problem("malformed-request", 400)).signIns.answer("totp", "1")).rejects.toThrow();
        await expect(over(problem("internal", 500)).signIns.answer("totp", "1")).rejects.toThrow();
        const down = new SignIns(createApi({ origin: "", fetcher: () => Promise.reject(new TypeError("offline")) }));
        await expect(down.answer("totp", "1")).rejects.toThrow("offline");
    });
});
