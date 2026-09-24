import assert from "node:assert/strict";
import { test } from "node:test";
import { createAuthClient, AuthError } from "./client";

test("auth mutations send same-origin cookies and a freshly obtained CSRF token", async () => {
    const requests: { url: string; init?: RequestInit }[] = [];
    const client = createAuthClient(async (url, init) => {
        requests.push({ url: String(url), ...(init ? { init } : {}) });
        return Response.json(requests.length === 1 ? { token: "csrf-test" } : { status: "issued" });
    });
    assert.deepEqual(await client.post("/login/password", { email: "me@example.test", password: "long test password" }), { status: "issued" });
    assert.equal(requests[0]?.url, "/api/v1/auth/csrf");
    assert.equal(requests[1]?.init?.credentials, "same-origin");
    assert.equal(new Headers(requests[1]?.init?.headers).get("X-CSRF-Token"), "csrf-test");
});

test("a failed CSRF request never submits credentials", async () => {
    let calls = 0;
    const client = createAuthClient(async () => { calls++; return Response.json({ detail: "Session expired" }, { status: 403 }); });
    await assert.rejects(client.post("/login/password", {}), AuthError);
    assert.equal(calls, 1);
});

test("server errors preserve retry-after without treating a failure as issued", async () => {
    const client = createAuthClient(async (url) => String(url).endsWith("/csrf")
        ? Response.json({ token: "csrf" })
        : Response.json({ detail: "Please try again later." }, { status: 429, headers: { "Retry-After": "60" } }));
    await assert.rejects(client.post("/email/start", {}), (error: unknown) => error instanceof AuthError && error.status === 429 && error.retryAfter === 60);
});
