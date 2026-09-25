import assert from "node:assert/strict";
import { test } from "node:test";
import { createAuthClient, AuthError } from "./client";

void test("auth mutations send same-origin cookies and a freshly obtained CSRF token", async () => {
    const requests: { url: string; init?: RequestInit }[] = [];
    const client = createAuthClient((url, init) => {
        const requestUrl = url instanceof Request ? url.url : url.toString();
        requests.push({ url: requestUrl, ...(init ? { init } : {}) });
        return Promise.resolve(Response.json(requests.length === 1 ? { token: "csrf-test" } : { status: "issued" }));
    });
    assert.deepEqual(await client.post("/login/password", { email: "me@example.test", password: "long test password" }), { status: "issued" });
    assert.equal(requests.at(0)?.url, "/api/v1/auth/csrf");
    const submitted = requests.at(1);
    assert.ok(submitted?.init);
    assert.equal(submitted.init.credentials, "same-origin");
    assert.equal(new Headers(submitted.init.headers).get("X-CSRF-Token"), "csrf-test");
});

void test("a failed CSRF request never submits credentials", async () => {
    let calls = 0;
    const client = createAuthClient(() => {
        calls++;
        return Promise.resolve(Response.json({ detail: "Session expired" }, { status: 403 }));
    });
    await assert.rejects(client.post("/login/password", {}), AuthError);
    assert.equal(calls, 1);
});

void test("server errors preserve retry-after without treating a failure as issued", async () => {
    const client = createAuthClient(url => {
        const requestUrl = url instanceof Request ? url.url : url.toString();
        const response = requestUrl.endsWith("/csrf")
            ? Response.json({ token: "csrf" })
            : Response.json({ detail: "Please try again later." }, { status: 429, headers: { "Retry-After": "60" } });
        return Promise.resolve(response);
    });
    await assert.rejects(client.post("/email/start", {}), (error: unknown) => error instanceof AuthError && error.status === 429 && error.retryAfter === 60);
});
