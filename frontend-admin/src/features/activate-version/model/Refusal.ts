import { ProblemError, StepUpDeclined } from "frontend-shared/api";
import { Rejection } from "@/shared/rejection";

/**
 * Why putting a version in force did not happen, in the terms the panel acts on:
 * the person said no to the confirmation (nothing broke), the version is gone, the version would leave no
 * administrator able to sign in, the reason was refused, or something else went wrong.
 */
export type Refusal = "declined" | "gone" | "lock-out" | "comment" | "failed";

export function refusalOf(failure: unknown): Refusal {
    if (failure instanceof StepUpDeclined) {
        return "declined";
    }
    if (failure instanceof ProblemError && failure.hasStatus(404)) {
        return "gone";
    }
    const rejection = Rejection.from(failure);
    if (rejection?.has("number", "would-lock-out-every-administrator") === true) {
        return "lock-out";
    }
    if (rejection?.has("comment") === true) {
        return "comment";
    }
    return "failed";
}
