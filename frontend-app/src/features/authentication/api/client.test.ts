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
    assert.deepEqual(await client.post("/login/password", { email: "me@example.test", password: "long test password", device: "Test" }), { status: "issued" });
    assert.equal(requests.at(0)?.url, "/api/v1/auth/csrf");
    const submitted = requests.at(1);
    assert.ok(submitted?.init);
    assert.equal(submitted.init.credentials, "same-origin");
    assert.equal(new Headers(submitted.init.headers).get("X-CSRF-Token"), "csrf-test");
});

void test("session refresh sends a CSRF-protected POST without a request body", async () => {
    const requests: { url: string; init?: RequestInit }[] = [];
    const client = createAuthClient((url, init) => {
        const requestUrl = url instanceof Request ? url.url : url.toString();
        requests.push({ url: requestUrl, ...(init ? { init } : {}) });
        return Promise.resolve(requests.length === 1
            ? Response.json({ token: "csrf-refresh" })
            : Response.json({ status: "issued" }));
    });

    await client.refreshSession();

    const refresh = requests.at(-1);
    assert.equal(refresh?.url, "/api/v1/auth/refresh");
    assert.equal(refresh?.init?.method, "POST");
    assert.equal(refresh?.init?.credentials, "same-origin");
    assert.equal(new Headers(refresh?.init?.headers).get("X-CSRF-Token"), "csrf-refresh");
    assert.equal(refresh?.init?.body, undefined);
});

void test("translates auth API JSON between camelCase UI models and snake_case wire fields", async () => {
    const requests: { url: string; init?: RequestInit }[] = [];
    const client = createAuthClient((url, init) => {
        const requestUrl = url instanceof Request ? url.url : url.toString();
        requests.push({ url: requestUrl, ...(init ? { init } : {}) });
        if (requestUrl.endsWith("/csrf")) return Promise.resolve(Response.json({ token: "csrf" }));
        return Promise.resolve(Response.json({ user_id: "user-123", challenge_id: "challenge-456" }));
    });

    const result = await client.post<{ userId: string; challengeId: string }>("/register/password", {
        email: "taylor@example.test",
        displayName: "Taylor",
        password: "long test password",
    });

    const wireBody = requests.at(1)?.init?.body;
    assert.ok(typeof wireBody === "string");
    assert.deepEqual(JSON.parse(wireBody), {
        email: "taylor@example.test",
        display_name: "Taylor",
        password: "long test password",
    });
    assert.deepEqual(result, { userId: "user-123", challengeId: "challenge-456" });
});

void test("a failed CSRF request never submits credentials", async () => {
    let calls = 0;
    const client = createAuthClient(() => {
        calls++;
        return Promise.resolve(Response.json({ detail: "Session expired" }, { status: 403 }));
    });
    await assert.rejects(client.post("/login/password", { email: "me@example.test", password: "long test password", device: "Test" }), AuthError);
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
    await assert.rejects(client.post("/login/email/code", { email: "me@example.test" }), (error: unknown) => error instanceof AuthError && error.status === 429 && error.retryAfter === 60);
});
