import { describe, expect, it } from "vitest";
import { createApi } from "frontend-shared/api";
import type { Device } from "../model/Device";
import { Devices } from "./Devices";

interface Sent {
    readonly method: string;
    readonly url: string;
    readonly body: string | undefined;
    readonly headers: Headers;
}

const NOT_FOUND = {
    type: "https://tallyvane.com/errors/not-found",
    title: "Not found",
    status: 404,
};

/** The whole client chain over a fake network, so the key, the path and the failures are the real ones. */
function over(...answers: Response[]): { devices: Devices; sent: Sent[] } {
    const sent: Sent[] = [];
    const fetcher: typeof fetch = (input, init) => {
        sent.push({
            method: init?.method ?? "GET",
            url: typeof input === "string" ? input : input instanceof URL ? input.href : input.url,
            body: typeof init?.body === "string" ? init.body : undefined,
            headers: new Headers(init?.headers),
        });
        return Promise.resolve(answers.shift() ?? new Response(null, { status: 204 }));
    };
    return { devices: new Devices(createApi({ origin: "", fetcher })), sent };
}

/** The device the list begins with, which the tests need and the types cannot promise. */
async function firstOf(devices: Devices): Promise<Device> {
    const [first] = await devices.list();
    if (first === undefined) {
        throw new Error("The scripted list has no device.");
    }
    return first;
}

async function secondOf(devices: Devices): Promise<Device> {
    const listed = await devices.list();
    const second = listed[1];
    if (second === undefined) {
        throw new Error("The scripted list has no second device.");
    }
    return second;
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function problem(body: object, status: number): Response {
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/problem+json" } });
}

const LISTED = {
    devices: [
        {
            id: "d-1",
            browser: "chrome",
            platform: "windows",
            mobile: false,
            name: "Work laptop",
            signed_in_at: "2026-10-01T08:00:00Z",
            last_active_at: "2026-10-03T11:59:00Z",
            current: true,
        },
        {
            id: "d-2",
            browser: "safari",
            platform: "ios",
            mobile: true,
            signed_in_at: "2026-09-29T08:00:00Z",
            last_active_at: "2026-10-03T09:00:00Z",
            current: false,
        },
    ],
};

describe("Devices", () => {
    it("lists the devices as they came, the one asking among them", async () => {
        const { devices, sent } = over(json(LISTED));

        const listed = await devices.list();

        expect(listed.map((device) => [device.key(), device.isCurrent(), device.givenName()])).toEqual([
            ["d-1", true, "Work laptop"],
            ["d-2", false, ""],
        ]);
        expect(sent[0]).toMatchObject({ method: "GET", url: "/api/v1/devices" });
    });

    it("signs out on another device with a key of its own, and a second time with another", async () => {
        const { devices, sent } = over(json(LISTED));
        const laptop = await secondOf(devices);

        await devices.signOut(laptop);
        await devices.signOut(laptop);

        const [, first, second] = sent;
        expect(first).toMatchObject({ method: "DELETE", url: "/api/v1/device/d-2" });
        expect(first?.headers.get("idempotency-key")).toBeTruthy();
        expect(second?.headers.get("idempotency-key")).not.toBe(first?.headers.get("idempotency-key"));
    });

    it("signs out here through the session the cookie holds, not the one that was listed", async () => {
        const { devices, sent } = over(json(LISTED));
        const here = await firstOf(devices);

        await devices.signOut(here);

        expect(here.isCurrent()).toBe(true);
        expect(sent[1]).toMatchObject({ method: "DELETE", url: "/api/v1/session" });
    });

    it("counts a device that has already gone as signed out", async () => {
        const { devices } = over(json(LISTED), problem(NOT_FOUND, 404));
        const laptop = await secondOf(devices);

        await expect(devices.signOut(laptop)).resolves.toBeUndefined();
    });

    it("does not hide any other failure of signing out", async () => {
        const { devices } = over(
            json(LISTED),
            problem({ type: "https://tallyvane.com/errors/unavailable", title: "Down", status: 503 }, 503),
        );
        const laptop = await secondOf(devices);

        await expect(devices.signOut(laptop)).rejects.toThrow("Down");
    });

    it("names a device with the name as the body", async () => {
        const { devices, sent } = over(json(LISTED));
        const laptop = await firstOf(devices);

        await devices.rename(laptop, "Desk");

        expect(sent[1]).toMatchObject({ method: "PUT", url: "/api/v1/device-names/d-1", body: '{"name":"Desk"}' });
        expect(sent[1]?.headers.get("content-type")).toBe("application/json");
        expect(sent[1]?.headers.get("idempotency-key")).toBeTruthy();
    });

    it("signs out everywhere else", async () => {
        const { devices, sent } = over();

        await devices.signOutOthers();

        expect(sent[0]).toMatchObject({ method: "DELETE", url: "/api/v1/other-devices" });
    });
});
