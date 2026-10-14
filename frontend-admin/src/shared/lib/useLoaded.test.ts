import { describe, expect, it } from "vitest";
import { mapLoaded, type Loaded } from "./useLoaded";

describe("mapLoaded", () => {
    it("shows a ready value another way and keeps it as stale as it was", () => {
        const ready: Loaded<number[]> = { status: "ready", value: [1, 2], stale: true };

        expect(mapLoaded(ready, (list) => list.length)).toEqual({ status: "ready", value: 2, stale: true });
    });

    it("passes loading and failure through unchanged", () => {
        const loading: Loaded<number> = { status: "loading" };
        const failed: Loaded<number> = { status: "failed" };

        expect(mapLoaded<number, number>(loading, (n) => n + 1)).toEqual({ status: "loading" });
        expect(mapLoaded<number, number>(failed, (n) => n + 1)).toEqual({ status: "failed" });
    });
});
