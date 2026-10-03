import { describe, expect, it } from "vitest";
import { createApi, StepUpDeclined, type StepUpHandler } from "frontend-shared/api";
import type { ConfirmationReaction } from "../model/Confirmation";
import { SecondFactors } from "./SecondFactors";

interface Sent {
    readonly method: string;
    readonly url: string;
    readonly body: string | undefined;
}

const CODES = ["AAAAA-BBBBB", "CCCCC-DDDDD", "EEEEE-FFFFF", "GGGGG-HHHHH", "JJJJJ-KKKKK", "LLLLL-MMMMM", "NNNNN-PPPPP", "QQQQQ-RRRRR", "SSSSS-TTTTT", "UUUUU-VVVVV"];

/** The whole client chain over a fake network, so the paths, keys and failures are the real ones. */
function over(onStepUp: StepUpHandler | undefined, ...answers: Response[]): { secondFactors: SecondFactors; sent: Sent[] } {
    const sent: Sent[] = [];
    const fetcher: typeof fetch = (input, init) => {
        sent.push({
            method: init?.method ?? "GET",
            url: typeof input === "string" ? input : input instanceof URL ? input.href : input.url,
            body: typeof init?.body === "string" ? init.body : undefined,
        });
        return Promise.resolve(answers.shift() ?? new Response(null, { status: 204 }));
    };
    return { secondFactors: new SecondFactors(createApi({ origin: "", fetcher, ...(onStepUp ? { onStepUp } : {}) })), sent };
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function problem(kind: string, status: number, errors?: unknown): Response {
    return new Response(JSON.stringify({ type: `https://tallyvane.com/errors/${kind}`, title: kind, status, ...(errors ? { errors } : {}) }), {
        status,
        headers: { "content-type": "application/problem+json" },
    });
}

const NAMED: ConfirmationReaction<string> = {
    confirmed: (codes) => `confirmed:${codes.each((code) => code).length.toString()}`,
    wrong: () => "wrong",
    conflict: () => "conflict",
};

describe("SecondFactors.standing", () => {
    it("reads what is set up and how many recovery codes are left", async () => {
        const { secondFactors, sent } = over(undefined, json({ standing: "retired", recovery_codes_remaining: 7 }));

        const standing = await secondFactors.standing();

        expect(sent[0]).toMatchObject({ method: "GET", url: "/api/v1/second-factor" });
        expect([standing.isOff(), standing.isActive(), standing.isRetired(), standing.codesLeft()]).toEqual([false, false, true, 7]);
    });
});

describe("SecondFactors.begin", () => {
    it("asks for a key and groups it in fours for typing", async () => {
        const { secondFactors, sent } = over(undefined, json({ key: "JBSWY3DPEHPK3PXP", uri: "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP" }));

        const key = await secondFactors.begin();

        expect(sent[0]).toMatchObject({ method: "POST", url: "/api/v1/totp-enrollments" });
        expect(key.spaced()).toBe("JBSW Y3DP EHPK 3PXP");
        expect(key.link()).toBe("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP");
    });

    it("goes on after the person has proved who they are again, and fails with StepUpDeclined when they decline", async () => {
        const proves = over({ confirm: () => Promise.resolve() }, problem("step-up-required", 403), json({ key: "AAAA", uri: "otpauth://x" }));
        const declines = over({ confirm: () => Promise.reject(new StepUpDeclined()) }, problem("step-up-required", 403));

        expect((await proves.secondFactors.begin()).spaced()).toBe("AAAA");
        expect(proves.sent).toHaveLength(2);
        await expect(declines.secondFactors.begin()).rejects.toBeInstanceOf(StepUpDeclined);
    });
});

describe("SecondFactors.confirm", () => {
    it("turns TOTP on with the first code and tells the ten recovery codes", async () => {
        const { secondFactors, sent } = over(undefined, json({ recovery_codes: CODES }));

        const confirmation = await secondFactors.confirm("123456");

        expect(sent[0]).toMatchObject({ method: "POST", url: "/api/v1/totp-confirmations", body: JSON.stringify({ code: "123456" }) });
        expect(confirmation.when(NAMED)).toBe("confirmed:10");
    });

    it("says a wrong code is wrong, and that a 409 is a conflict", async () => {
        const wrong = problem("validation-failed", 422, [{ field: "code", code: "wrong-code" }]);

        expect((await over(undefined, wrong).secondFactors.confirm("000000")).when(NAMED)).toBe("wrong");
        expect((await over(undefined, problem("conflict", 409)).secondFactors.confirm("000000")).when(NAMED)).toBe("conflict");
    });

    it("fails for what is not an answer", async () => {
        await expect(over(undefined, problem("internal", 500)).secondFactors.confirm("000000")).rejects.toThrow();
    });
});

describe("SecondFactors.disable", () => {
    it("removes the enrolment", async () => {
        const { secondFactors, sent } = over(undefined, new Response(null, { status: 204 }));

        await secondFactors.disable();

        expect(sent[0]).toMatchObject({ method: "DELETE", url: "/api/v1/totp-enrollment" });
    });

    it("counts nothing left to remove (409) as done, and fails for anything else", async () => {
        await expect(over(undefined, problem("conflict", 409)).secondFactors.disable()).resolves.toBeUndefined();
        await expect(over(undefined, problem("internal", 500)).secondFactors.disable()).rejects.toThrow();
    });
});

describe("SecondFactors.reissue", () => {
    it("asks for ten new codes", async () => {
        const { secondFactors, sent } = over(undefined, json({ recovery_codes: CODES }));

        const codes = await secondFactors.reissue();

        expect(sent[0]).toMatchObject({ method: "POST", url: "/api/v1/recovery-codes" });
        expect(codes.asText()).toBe(`${CODES.join("\n")}\n`);
    });
});
