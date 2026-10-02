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
});
