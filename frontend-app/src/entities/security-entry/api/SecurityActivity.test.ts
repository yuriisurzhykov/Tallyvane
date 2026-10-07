import { describe, expect, it } from "vitest";
import { createApi, ProblemError } from "frontend-shared/api";
import { SecurityActivity } from "./SecurityActivity";

const FIRST_PAGE = {
    entries: [
        { kind: "totp_turned_off", occurred_at: "2026-10-06T14:32:00Z", first_from_device: false },
        { kind: "signed_in", occurred_at: "2026-10-06T09:00:00Z", first_from_device: true },
    ],
    next: "41",
};

/** The whole client chain over a fake network, so the path and the failures are the real ones. */
function over(...answers: Response[]): { activity: SecurityActivity; urls: string[] } {
    const urls: string[] = [];
    const fetcher: typeof fetch = (input) => {
        urls.push(typeof input === "string" ? input : input instanceof URL ? input.href : input.url);
        return Promise.resolve(answers.shift() ?? new Response(null, { status: 204 }));
    };
    return { activity: new SecurityActivity(createApi({ origin: "", fetcher })), urls };
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

describe("SecurityActivity.page", () => {
    it("asks for the newest entries when it has no cursor", async () => {
        const { activity, urls } = over(json(FIRST_PAGE));

        const log = await activity.page(undefined);

        expect(urls).toEqual(["/api/v1/security-activity"]);
        expect(log.map((entry) => entry.kind())).toEqual(["totp_turned_off", "signed_in"]);
        expect(log.after()).toBe("41");
    });

    it("hands the cursor back as it was given", async () => {
        const { activity, urls } = over(json({ entries: [] }));

        const log = await activity.page("41");

        expect(urls).toEqual(["/api/v1/security-activity?before=41"]);
        expect(log.isEmpty()).toBe(true);
        expect(log.hasMore()).toBe(false);
    });

    it("fails when the server does", async () => {
        const { activity } = over(
            json({ type: "https://tallyvane.com/errors/unavailable", title: "Unavailable", status: 503 }, 503),
        );

        await expect(activity.page(undefined)).rejects.toBeInstanceOf(ProblemError);
    });
});
