/* global test, expect */

import { AdminAuthError, createAdminAuthClient } from "./client.ts";

void test("password sign-in sends CSRF and same-origin credentials", async () => {
    const calls = [];
    const client = createAdminAuthClient(async (input, init) => {
        calls.push({ input: String(input), init });
        if (String(input).endsWith("/csrf")) return Response.json({ token: "csrf-value" });
        return Response.json({
            status: "requires_second_factor",
            pending_id: "pending-id",
            available_methods: ["BACKUP_CODE"],
        });
    });

    const result = await client.signIn("admin@example.test", "test-password");

    expect(result).toEqual({
        status: "requires_second_factor",
        pendingId: "pending-id",
        availableMethods: ["BACKUP_CODE"],
    });
    expect(calls.map(call => call.input)).toEqual([
        "/api/v1/auth/csrf",
        "/api/v1/auth/login/password",
    ]);
    expect(calls[1].init.credentials).toBe("same-origin");
    expect(new Headers(calls[1].init.headers).get("X-CSRF-Token")).toBe("csrf-value");
    expect(JSON.parse(calls[1].init.body)).toEqual({
        email: "admin@example.test",
        password: "test-password",
        device: "Admin browser",
    });
});

void test("second-factor verification serializes pendingId as pending_id", async () => {
    const calls = [];
    const client = createAdminAuthClient(async (input, init) => {
        calls.push({ input: String(input), init });
        if (String(input).endsWith("/csrf")) return Response.json({ token: "csrf-value" });
        return Response.json({ status: "issued" });
    });

    await client.verifyFactor("pending-id", "BACKUP_CODE", "backup-code");

    expect(JSON.parse(calls[1].init.body)).toEqual({
        pending_id: "pending-id",
        kind: "BACKUP_CODE",
        code: "backup-code",
    });
});

void test("authentication client retains refused response status", async () => {
    let requestCount = 0;
    const client = createAdminAuthClient(async () => {
        requestCount += 1;
        return requestCount === 1
            ? Response.json({ token: "csrf-value" })
            : new Response(null, { status: 401 });
    });

    await expect(client.signIn("admin@example.test", "wrong-password")).rejects.toMatchObject({
        constructor: AdminAuthError,
        status: 401,
    });
});
