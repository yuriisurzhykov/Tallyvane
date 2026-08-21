import * as path from "node:path";
import type { FullResult, Reporter, TestCase, TestResult } from "@playwright/test/reporter";

/**
 * The local-run answer to `list`'s own wall of output: one line per test,
 * whether it passed or not, which is unreadable the moment a suite crosses a
 * few hundred tests — exactly what this repo's contrast suites already do.
 * This reporter prints nothing for a passing or skipped test, and exactly one
 * line for a failing one: `<file>:<line> › <title> — <reason>`. `SummaryReporter`
 * still runs alongside it and writes the full JSON digest to
 * `test-results/summary.json` for anyone who needs more than one line.
 *
 * Deliberately not a replacement for `list`/`github` everywhere — `createReporters()`
 * picks this one specifically for local runs, where a person is watching the
 * terminal live and CI's own PR-comment digest is not in play.
 */
export default class CompactReporter implements Reporter {
    private passed = 0;
    private failed = 0;
    private skipped = 0;
    private readonly startedAt = Date.now();

    public onTestEnd(test: TestCase, result: TestResult): void {
        if (result.status === "passed") {
            this.passed += 1;
            return;
        }
        if (result.status === "skipped") {
            this.skipped += 1;
            return;
        }
        this.failed += 1;

        const location = `${path.relative(process.cwd(), test.location.file)}:${String(test.location.line)}`;
        const reason = firstLine(result.errors[0]?.message) ?? result.status;
        // eslint-disable-next-line no-console -- this reporter's entire job is printing to the terminal.
        console.log(`✗ ${location} › ${test.titlePath().filter(Boolean).join(" > ")} — ${reason}`);
    }

    public onEnd(result: FullResult): void {
        const seconds = ((Date.now() - this.startedAt) / 1000).toFixed(1);
        const icon = result.status === "passed" ? "✓" : "✗";
        // eslint-disable-next-line no-console -- see above.
        console.log(
            `${icon} ${String(this.passed)} passed, ${String(this.failed)} failed, ${String(this.skipped)} skipped (${seconds}s). ` +
                "Full detail: test-results/summary.json, or the HTML report (`playwright show-report`).",
        );
    }
}

/**
 * Playwright's own error messages carry the full expect diff — the array of
 * every sample or every axe violation, sometimes hundreds of lines — below a
 * first line that already names the real failure (`Error: no text was found
 * to measure`, `APCA contrast is insufficient:`). Only that first line
 * belongs on a single-line report; the rest is exactly what
 * `test-results/summary.json` and the HTML report already carry in full.
 *
 * The ANSI codes `expect`'s own diff colouring embeds are meant for a
 * colour-aware terminal repainting them live — captured into a log file or a
 * non-colour terminal instead, they show up as literal escape sequences,
 * noise this one-line report cannot afford.
 */
// eslint-disable-next-line no-control-regex -- the ESC byte is exactly what an ANSI escape sequence starts with; matching it is the point, not an accident.
const ANSI_ESCAPE_PATTERN = /\u001b\[[0-9;]*m/g;

function firstLine(message: string | undefined): string | null {
    if (!message) return null;
    // A ternary, not `?? null`: an all-ANSI/whitespace message trims down to
    // an empty (falsy, not nullish) string, which must fall back to `null`
    // the same as a missing message does — `??` would let the empty string
    // through and print a reason-less `— ` in the report.
    const line = message.replaceAll(ANSI_ESCAPE_PATTERN, "").split("\n")[0]?.trim();
    // Not `line ?? null`, despite the rule's own suggestion: `line`'s type is
    // `string | undefined`, and an empty string is falsy but not nullish —
    // `??` would let it through, which is exactly the bug the comment above
    // exists to prevent. The rule's "a ? a : b" heuristic doesn't account for
    // a non-nullish falsy value on the left, so its suggestion is unsound here.
    // eslint-disable-next-line @typescript-eslint/prefer-nullish-coalescing -- see comment above
    return line ? line : null;
}
