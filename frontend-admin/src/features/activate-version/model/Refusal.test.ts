import { describe, expect, it } from "vitest";
import { ProblemError, StepUpDeclined } from "frontend-shared/api";
import { refusalOf } from "./Refusal";

const problem = (status: number, errors?: readonly { field: string; code: string }[]) =>
    ProblemError.from({
        status,
        headers: new Headers(),
        body: { type: "https://tallyvane.com/errors/validation-failed", title: "x", status, ...(errors ? { errors } : {}) },
    });

describe("refusalOf", () => {
    it("takes a declined confirmation for the person's own no", () => {
        expect(refusalOf(new StepUpDeclined())).toBe("declined");
    });

    it("knows that the version is gone", () => {
        expect(refusalOf(problem(404))).toBe("gone");
    });

    it("knows a version that would leave no administrator able to sign in", () => {
        expect(refusalOf(problem(422, [{ field: "number", code: "would-lock-out-every-administrator" }]))).toBe("lock-out");
    });

    it("knows a refused reason", () => {
        expect(refusalOf(problem(422, [{ field: "comment", code: "comment-invalid" }]))).toBe("comment");
    });

    it("calls everything else a failure, including a 422 it has no meaning for", () => {
        expect(refusalOf(problem(422, [{ field: "purpose", code: "unknown" }]))).toBe("failed");
        expect(refusalOf(problem(500))).toBe("failed");
        expect(refusalOf(new Error("offline"))).toBe("failed");
    });
});
