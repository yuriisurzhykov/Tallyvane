import { describe, expect, it } from "vitest";
import { Api } from "./Api";
import { ScriptedTransport, ok } from "./testing";

describe("Api", () => {
    it("reads who is signed in as the typed Me of the specification", async () => {
        const wire = new ScriptedTransport(ok({ id: "a-1", name: "Yurii" }));

        const me = await new Api(wire).get("/me");

        expect(me.name).toBe("Yurii");
        expect(wire.requests[0]).toMatchObject({ method: "GET", path: "/me" });
    });

    it("sends the body of a registration and the key the caller chose", async () => {
        const wire = new ScriptedTransport(ok(undefined, 204));

        await new Api(wire).post("/registration", { name: "Yurii", agreed: true }, { idempotencyKey: "k-1" });

        expect(wire.requests[0]).toEqual({
            method: "POST",
            path: "/registration",
            body: { name: "Yurii", agreed: true },
            headers: { "idempotency-key": "k-1" },
        });
    });

    it("sends a POST with no body as a bare intention", async () => {
        const wire = new ScriptedTransport(ok(undefined, 204));

        await new Api(wire).trigger("/sessions");
        await new Api(wire).delete("/session");

        expect(wire.requests.map((sent) => [sent.method, sent.path, "body" in sent])).toEqual([
            ["POST", "/sessions", false],
            ["DELETE", "/session", false],
        ]);
    });

    it("fills the segments of a path from the params, escaped, and sends the key with them", async () => {
        const wire = new ScriptedTransport(ok(undefined, 204));

        await new Api(wire).delete("/device/{id}", { params: { id: "a/b c" }, idempotencyKey: "k-2" });

        expect(wire.requests[0]).toEqual({
            method: "DELETE",
            path: "/device/a%2Fb%20c",
            headers: { "idempotency-key": "k-2" },
        });
    });

    it("sends a PUT with its body to the filled path", async () => {
        const wire = new ScriptedTransport(ok(undefined, 204));

        await new Api(wire).put("/device-names/{id}", { name: "Work laptop" }, { params: { id: "d-1" } });

        expect(wire.requests[0]).toEqual({
            method: "PUT",
            path: "/device-names/d-1",
            body: { name: "Work laptop" },
            headers: {},
        });
    });

    it("refuses to send a path whose segment has no value", async () => {
        const wire = new ScriptedTransport();
        const api = new Api(wire);

        // The types forbid leaving the params out; this is the guard for a caller that got past them.
        const unchecked = api.delete.bind(api) as (path: string, options: object) => Promise<unknown>;

        await expect(unchecked("/device/{id}", { params: {} })).rejects.toThrow("{id}");
        expect(wire.requests).toEqual([]);
    });
});
