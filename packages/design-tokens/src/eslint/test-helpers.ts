import type { Linter } from "eslint";

/**
 * Every one of this package's rule tests either asserts a single finding or
 * none — this is the "single finding" half. Asserting the count and
 * returning its message text in one step means the two can never drift out
 * of sync, and the array access needs no non-null assertion: destructuring
 * plus a length check on the rest is a real type guard, unlike indexing
 * `messages[0]` directly, which `Linter.LintMessage[]`'s type alone can
 * never prove non-empty.
 */
export function onlyMessage(messages: readonly Linter.LintMessage[]): string {
    const [message, ...rest] = messages;
    if (!message || rest.length > 0) {
        throw new Error(`Expected exactly one lint message, got ${String(messages.length)}`);
    }
    return message.message;
}
