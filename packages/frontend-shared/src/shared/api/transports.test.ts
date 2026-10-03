import { describe, expect, it } from "vitest";
import { ConfirmingTransport } from "./ConfirmingTransport";
import { IdempotentTransport } from "./IdempotentTransport";
import { ProblemError } from "./ProblemError";
import { ProblemTransport } from "./ProblemTransport";
import { ReauthenticatingTransport } from "./ReauthenticatingTransport";
import { SingleFlight } from "./SingleFlight";
import { ScriptedTransport, ok, problem, request } from "./testing";

const sequentialKeys = () => {
    let next = 0;
    return { fresh: () => `key-${String(++next)}` };
};

describe("IdempotentTransport", () => {
    it("gives every unsafe request a key and a safe one none", async () => {
        const wire = new ScriptedTransport();
        const transport = new IdempotentTransport(wire, sequentialKeys());

        await transport.send(request("POST", "/sessions"));
        await transport.send(request("DELETE", "/session"));
        await transport.send(request("GET", "/me"));

        expect(wire.requests.map((sent) => sent.headers["idempotency-key"])).toEqual(["key-1", "key-2", undefined]);
    });

    it("leaves a key the caller chose alone", async () => {
        const wire = new ScriptedTransport();

        await new IdempotentTransport(wire, sequentialKeys()).send(request("POST", "/registration", { "idempotency-key": "mine" }));

        expect(wire.requests[0]?.headers["idempotency-key"]).toBe("mine");
    });
});

describe("ProblemTransport", () => {
    it("throws a ProblemError that knows its kind for a failure", async () => {
        const transport = new ProblemTransport(new ScriptedTransport(problem("session-expired", 401)));

        const failure = await transport.send(request("GET", "/me")).catch((error: unknown) => error);

        expect(failure).toBeInstanceOf(ProblemError);
        expect((failure as ProblemError).isSessionExpired()).toBe(true);
    });

    it("makes a problem of an answer that is not one, such as a proxy's error page", async () => {
        const html = { status: 502, body: "<html>Bad gateway</html>", headers: new Headers() };

        const failure = await new ProblemTransport(new ScriptedTransport(html)).send(request("GET", "/me")).catch((error: unknown) => error);

        expect((failure as ProblemError).kind()).toBe("other");
        expect((failure as ProblemError).hasStatus(502)).toBe(true);
    });

    it("tells a 409 that was carried out from one still running by Retry-After", () => {
        expect(ProblemError.from(problem("conflict", 409)).isAnsweredBefore()).toBe(true);
        expect(ProblemError.from(problem("conflict", 409, { "retry-after": "3" })).isAnsweredBefore()).toBe(false);
    });

    it("names the code of a field the server refused", () => {
        const refused = ProblemError.from({
            status: 422,
            headers: new Headers(),
            body: {
                type: "https://tallyvane.com/errors/validation-failed",
                title: "Invalid",
                status: 422,
                errors: [{ field: "agreed", code: "consent-required" }],
            },
        });

        expect(refused.fieldCode("agreed")).toBe("consent-required");
        expect(refused.fieldCode("name")).toBeUndefined();
    });
});

describe("ReauthenticatingTransport", () => {
    const expired = () => ProblemError.from(problem("session-expired", 401));

    it("waits for the handler, then repeats the very same request once", async () => {
        const wire = new ScriptedTransport(expired(), ok("saved"));
        const calls: string[] = [];
        const transport = new ReauthenticatingTransport(wire, { reauthenticate: () => { calls.push("signed in"); return Promise.resolve(); } });
        const sent = request("POST", "/notes", { "idempotency-key": "k" });

        const answer = await transport.send(sent);

        expect(answer.body).toBe("saved");
        expect(calls).toEqual(["signed in"]);
        expect(wire.requests).toEqual([sent, sent]);
    });

    it("opens one sign-in for requests that expire together", async () => {
        const wire = new ScriptedTransport(expired(), expired(), expired(), ok(1), ok(2), ok(3));
        let opened = 0;
        let finish = (): void => undefined;
        const gate = new Promise<void>((resolve) => { finish = resolve; });
        const transport = new ReauthenticatingTransport(wire, { reauthenticate: () => { opened += 1; return gate; } });

        const answers = Promise.all([1, 2, 3].map(() => transport.send(request("GET", "/x"))));
        await Promise.resolve();
        finish();
        await answers;

        expect(opened).toBe(1);
    });

    it("does not touch a failure that is not an ended session", async () => {
        const wire = new ScriptedTransport(ProblemError.from(problem("forbidden", 403)));
        const transport = new ReauthenticatingTransport(wire, { reauthenticate: () => Promise.reject(new Error("must not be asked")) });

        await expect(transport.send(request("GET", "/x"))).rejects.toBeInstanceOf(ProblemError);
        expect(wire.requests).toHaveLength(1);
    });

    it("lets a second ended session through instead of looping", async () => {
        const wire = new ScriptedTransport(expired(), expired());
        const transport = new ReauthenticatingTransport(wire, { reauthenticate: () => Promise.resolve() });

        await expect(transport.send(request("GET", "/x"))).rejects.toBeInstanceOf(ProblemError);
        expect(wire.requests).toHaveLength(2);
    });

    it("asks again for a later expiry once the first sign-in is over", async () => {
        const flight = new SingleFlight();
        let runs = 0;

        await flight.run(() => { runs += 1; return Promise.resolve(); });
        await flight.run(() => { runs += 1; return Promise.resolve(); });

        expect(runs).toBe(2);
    });
});

describe("ConfirmingTransport", () => {
    const stale = () => ProblemError.from(problem("step-up-required", 403));

    it("waits for the handler, then repeats the very same request once", async () => {
        const wire = new ScriptedTransport(stale(), ok("done"));
        const calls: string[] = [];
        const transport = new ConfirmingTransport(wire, { confirm: () => { calls.push("confirmed"); return Promise.resolve(); } });
        const sent = request("DELETE", "/other-devices", { "idempotency-key": "k" });

        const answer = await transport.send(sent);

        expect(answer.body).toBe("done");
        expect(calls).toEqual(["confirmed"]);
        expect(wire.requests).toEqual([sent, sent]);
    });

    it("opens one confirmation for acts refused together", async () => {
        const wire = new ScriptedTransport(stale(), stale(), ok(1), ok(2));
        let opened = 0;
        let finish = (): void => undefined;
        const gate = new Promise<void>((resolve) => { finish = resolve; });
        const transport = new ConfirmingTransport(wire, { confirm: () => { opened += 1; return gate; } });

        const answers = Promise.all([1, 2].map(() => transport.send(request("DELETE", "/x"))));
        await Promise.resolve();
        finish();
        await answers;

        expect(opened).toBe(1);
    });

    it("does not touch a failure that is not a missing proof, an ended session included", async () => {
        const wire = new ScriptedTransport(ProblemError.from(problem("session-expired", 401)));
        const transport = new ConfirmingTransport(wire, { confirm: () => Promise.reject(new Error("must not be asked")) });

        await expect(transport.send(request("DELETE", "/x"))).rejects.toBeInstanceOf(ProblemError);
        expect(wire.requests).toHaveLength(1);
    });

    it("lets a second refusal through instead of looping", async () => {
        const wire = new ScriptedTransport(stale(), stale());
        const transport = new ConfirmingTransport(wire, { confirm: () => Promise.resolve() });

        await expect(transport.send(request("DELETE", "/x"))).rejects.toBeInstanceOf(ProblemError);
        expect(wire.requests).toHaveLength(2);
    });

    it("passes the failure on when the person gives up", async () => {
        const wire = new ScriptedTransport(stale());
        const transport = new ConfirmingTransport(wire, { confirm: () => Promise.reject(new Error("closed")) });

        await expect(transport.send(request("DELETE", "/x"))).rejects.toThrow("closed");
    });
});
