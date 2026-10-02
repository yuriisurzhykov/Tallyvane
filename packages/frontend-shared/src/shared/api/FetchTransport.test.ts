import { describe, expect, it, vi } from "vitest";
import { FetchTransport } from "./FetchTransport";
import { request } from "./testing";

const json = (body: unknown, init: ResponseInit = {}) =>
    new Response(JSON.stringify(body), { headers: { "content-type": "application/json" }, ...init });

describe("FetchTransport", () => {
    it("calls the global fetch plainly, not as a method of itself (a browser refuses that)", async () => {
        const receivers: unknown[] = [];
        vi.stubGlobal("fetch", function (this: unknown) {
            receivers.push(this);
            return Promise.resolve(json({}));
        });
        await new FetchTransport({ origin: "" }).send(request("GET", "/me"));
        vi.unstubAllGlobals();
        expect(receivers).toEqual([undefined]);
    });

    it("goes to the API prefix of the origin with the headers it was given and the request's own", async () => {
        const fetcher = vi.fn(() => Promise.resolve(json({ ok: true })));
        const transport = new FetchTransport({ origin: "http://nginx", headers: { cookie: "a=b", host: "localhost:8080" }, fetcher });

        const answer = await transport.send({ method: "POST", path: "/registration", body: { name: "Y" }, headers: { "idempotency-key": "k" } });

        const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toBe("http://nginx/api/v1/registration");
        expect(init.body).toBe('{"name":"Y"}');
        expect(init.headers).toMatchObject({ cookie: "a=b", host: "localhost:8080", "idempotency-key": "k", "content-type": "application/json" });
        expect(answer.body).toEqual({ ok: true });
    });

    it("reads an empty answer as no body and a JSON answer as JSON", async () => {
        const empty = new FetchTransport({ origin: "", fetcher: () => Promise.resolve(new Response(null, { status: 204 })) });

        expect((await empty.send(request("DELETE", "/session"))).body).toBeUndefined();
    });
});
