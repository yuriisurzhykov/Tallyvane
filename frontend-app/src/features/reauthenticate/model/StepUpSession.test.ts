import { describe, expect, it } from "vitest";
import { StepUpDeclined } from "frontend-shared/api";
import { StepUpSession } from "./StepUpSession";

describe("StepUpSession", () => {
    it("waits from the first ask until the person has confirmed, and lets the asker go on", async () => {
        const session = new StepUpSession();

        const asked = session.confirm();
        expect(session.isWaiting()).toBe(true);
        session.done();

        await expect(asked).resolves.toBeUndefined();
        expect(session.isWaiting()).toBe(false);
    });

    it("gives everyone who asks meanwhile the same wait", async () => {
        const session = new StepUpSession();

        const first = session.confirm();
        const second = session.confirm();

        expect(second).toBe(first);
        session.done();
        await first;
    });

    it("fails the asker when the person gives up, and asks afresh the next time", async () => {
        const session = new StepUpSession();
        const asked = session.confirm();

        session.cancel();

        await expect(asked).rejects.toBeInstanceOf(StepUpDeclined);
        expect(session.isWaiting()).toBe(false);
        const again = session.confirm();
        expect(again).not.toBe(asked);
        session.done();
        await again;
    });

    it("tells watchers when it starts and stops waiting, until they stop watching", () => {
        const session = new StepUpSession();
        let told = 0;
        const stop = session.subscribe(() => {
            told += 1;
        });

        void session.confirm();
        session.done();
        stop();
        void session.confirm();

        expect(told).toBe(2);
    });
});
