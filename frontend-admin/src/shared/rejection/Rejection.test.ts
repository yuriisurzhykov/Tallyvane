import { describe, expect, it } from "vitest";
import { ProblemError } from "frontend-shared/api";
import { Rejection } from "./Rejection";

const refused = (status: number, errors?: readonly { field: string; code: string }[], detail?: string) =>
    ProblemError.from({
        status,
        headers: new Headers(),
        body: { type: "https://tallyvane.com/errors/validation-failed", title: "Invalid", status, ...(errors ? { errors } : {}), ...(detail ? { detail } : {}) },
    });

describe("Rejection", () => {
    it("is made of a 422 and of nothing else", () => {
        expect(Rejection.from(refused(422, [{ field: "comment", code: "comment-invalid" }]))).toBeDefined();
        expect(Rejection.from(refused(500))).toBeUndefined();
        expect(Rejection.from(new Error("offline"))).toBeUndefined();
    });

    it("knows what is wrong with a field, and with a field's code", () => {
        const rejection = Rejection.from(refused(422, [{ field: "idle_millis", code: "out-of-bounds" }]));

        expect(rejection?.has("idle_millis")).toBe(true);
        expect(rejection?.has("idle_millis", "out-of-bounds")).toBe(true);
        expect(rejection?.has("idle_millis", "below-idle")).toBe(false);
        expect(rejection?.has("absolute_millis")).toBe(false);
    });

    it("splits what belongs next to a field from the rest", () => {
        const rejection = Rejection.from(refused(422, [
            { field: "max_failures", code: "out-of-bounds" },
            { field: "steps", code: "below-the-floor" },
        ]));

        expect(rejection?.about("max_failures").isEmpty()).toBe(false);
        expect(rejection?.about("steps").has("max_failures")).toBe(false);
        expect(rejection?.except("max_failures").has("steps")).toBe(true);
        expect(rejection?.except("max_failures").has("max_failures")).toBe(false);
    });

    it("words every fault in the server's order", () => {
        const rejection = Rejection.from(refused(422, [
            { field: "steps", code: "below-the-floor" },
            { field: "comment", code: "comment-invalid" },
        ]));

        const sentences = rejection?.describe((fault) => `${fault.field}: ${fault.code}`, () => "unnamed");

        expect(sentences).toEqual(["steps: below-the-floor", "comment: comment-invalid"]);
    });

    it("has nothing to say about a field it found nothing wrong with", () => {
        const rejection = Rejection.from(refused(422, [{ field: "comment", code: "comment-invalid" }]));

        expect(rejection?.about("max_failures").describe(() => "never")).toEqual([]);
    });

    it("says the server's own words when it named no field", () => {
        const rejection = Rejection.from(refused(422, undefined, "Nothing makes sense."));

        expect(rejection?.describe(() => "never", (detail) => detail ?? "none")).toEqual(["Nothing makes sense."]);
    });
});
