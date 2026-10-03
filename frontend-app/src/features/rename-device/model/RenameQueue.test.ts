import { describe, expect, it } from "vitest";
import { RenameQueue } from "./RenameQueue";

/** A task the test finishes by hand, so that the order things happen in is the test's, not the clock's. */
function gate(): { task: () => Promise<void>; started: () => boolean; finish: () => void; fail: () => void } {
    let begun = false;
    let release: (failed: boolean) => void = () => undefined;
    const held = new Promise<boolean>((resolve) => {
        release = resolve;
    });
    return {
        task: async () => {
            begun = true;
            if (await held) {
                throw new Error("refused");
            }
        },
        started: () => begun,
        finish: () => { release(false); },
        fail: () => { release(true); },
    };
}

const settle = () => new Promise<void>((resolve) => { setTimeout(resolve, 0); });

describe("RenameQueue", () => {
    it("does not start a rename while an earlier one is still in flight", async () => {
        const queue = new RenameQueue();
        const first = gate();
        const second = gate();

        const one = queue.run(first.task);
        const two = queue.run(second.task);
        await settle();

        expect(first.started()).toBe(true);
        expect(second.started()).toBe(false);

        first.finish();
        await settle();
        expect(second.started()).toBe(true);

        second.finish();
        await Promise.all([one, two]);
    });

    it("says the list is worth reading again only after the last one", async () => {
        const queue = new RenameQueue();
        const first = gate();
        const second = gate();

        const one = queue.run(first.task);
        const two = queue.run(second.task);
        first.finish();
        second.finish();

        await expect(one).resolves.toBe(false);
        await expect(two).resolves.toBe(true);
    });

    it("lets the next rename run after one is refused, and reports the refusal to its own caller", async () => {
        const queue = new RenameQueue();
        const first = gate();
        const second = gate();

        const one = queue.run(first.task);
        const two = queue.run(second.task);
        first.fail();
        second.finish();

        await expect(one).rejects.toThrow("refused");
        await expect(two).resolves.toBe(true);
    });
});
