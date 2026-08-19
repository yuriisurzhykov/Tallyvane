import * as fs from "node:fs";
import * as path from "node:path";
import type { FullResult, Reporter, TestCase, TestResult } from "@playwright/test/reporter";

/**
 * Writes a flat JSON digest of the run, so a CI step — a pull-request comment,
 * a dashboard, a gate on some threshold — can read the outcome without parsing
 * Playwright's own report format, which is meant for a browser rather than for
 * a script.
 *
 * It never replaces the HTML report. That one is for a person looking at a
 * failed screenshot diff; this one is for a machine deciding what to say about
 * the run.
 *
 * Two families of finding are digested, and they come from different places on
 * purpose. Structural accessibility comes from axe; colour contrast does not,
 * because axe implements the WCAG 2 ratio and this project measures with APCA
 * instead. A summary that only counted axe violations would report a clean run
 * on a page whose text is unreadable.
 */

interface AxeViolationSummary {
    readonly id: string;
    readonly impact: string | null;
    readonly help: string;
    readonly nodes: number;
}

interface ContrastFindingSummary {
    readonly selector: string;
    readonly lc: number;
    readonly fontSize: number;
    /** The smallest size that would pass at the measured contrast and weight. Above the plausible range means no size would. */
    readonly requiredSize: number;
}

interface TestSummaryEntry {
    readonly title: string;
    readonly project: string;
    readonly status: TestResult["status"];
    readonly durationMs: number;
    readonly errorMessage?: string;
    readonly a11yViolations?: readonly AxeViolationSummary[];
    readonly contrastFindings?: readonly ContrastFindingSummary[];
}

interface Summary {
    readonly generatedAt: string;
    readonly overallStatus: FullResult["status"];
    readonly counts: { readonly passed: number; readonly failed: number; readonly skipped: number; readonly total: number };
    readonly a11yViolationCount: number;
    readonly contrastFindingCount: number;
    readonly tests: readonly TestSummaryEntry[];
}

const AXE_ATTACHMENT = "axe-results";
const CONTRAST_ATTACHMENT = "apca-findings";

/** A malformed attachment yields nothing rather than throwing: a reporter that crashes takes the run's result with it, which is a worse outcome than an incomplete digest. */
function parseAttachment<T>(raw: string, map: (value: unknown) => T): T[] {
    try {
        const parsed: unknown = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed.map(map) : [];
    } catch {
        return [];
    }
}

function toAxeSummary(value: unknown): AxeViolationSummary {
    const violation = value as { id?: string; impact?: string | null; help?: string; nodes?: unknown[] };
    return {
        id: violation.id ?? "unknown",
        impact: violation.impact ?? null,
        help: violation.help ?? "",
        nodes: violation.nodes?.length ?? 0,
    };
}

function toContrastSummary(value: unknown): ContrastFindingSummary {
    const finding = value as { selector?: string; lc?: number; fontSize?: number; requiredSize?: number };
    return {
        selector: finding.selector ?? "unknown",
        lc: finding.lc ?? 0,
        fontSize: finding.fontSize ?? 0,
        requiredSize: finding.requiredSize ?? 0,
    };
}

function readAttachment(result: TestResult, name: string): string | null {
    const attachment = result.attachments.find((candidate) => candidate.name === name);
    if (attachment?.body) return attachment.body.toString("utf-8");
    // Playwright spills large attachments to disk instead of keeping them in
    // memory, so a body-only reader silently loses exactly the biggest ones.
    if (attachment?.path) {
        try {
            return fs.readFileSync(attachment.path, "utf-8");
        } catch {
            return null;
        }
    }
    return null;
}

export default class SummaryReporter implements Reporter {
    private readonly entries: TestSummaryEntry[] = [];
    private readonly outputFile: string;

    constructor(options: { readonly outputFile?: string } = {}) {
        this.outputFile = options.outputFile ?? path.join("test-results", "summary.json");
    }

    onTestEnd(test: TestCase, result: TestResult): void {
        const axeRaw = readAttachment(result, AXE_ATTACHMENT);
        const contrastRaw = readAttachment(result, CONTRAST_ATTACHMENT);

        this.entries.push({
            title: test.titlePath().filter(Boolean).join(" > "),
            project: test.parent.project()?.name ?? "unknown",
            status: result.status,
            durationMs: result.duration,
            ...(result.status !== "passed" && result.errors[0]?.message
                ? { errorMessage: result.errors[0].message }
                : {}),
            ...(axeRaw ? { a11yViolations: parseAttachment(axeRaw, toAxeSummary) } : {}),
            ...(contrastRaw ? { contrastFindings: parseAttachment(contrastRaw, toContrastSummary) } : {}),
        });
    }

    onEnd(result: FullResult): void {
        const passed = this.entries.filter((entry) => entry.status === "passed").length;
        const skipped = this.entries.filter((entry) => entry.status === "skipped").length;

        const summary: Summary = {
            generatedAt: new Date().toISOString(),
            overallStatus: result.status,
            counts: {
                passed,
                failed: this.entries.length - passed - skipped,
                skipped,
                total: this.entries.length,
            },
            a11yViolationCount: this.entries.reduce((sum, entry) => sum + (entry.a11yViolations?.length ?? 0), 0),
            contrastFindingCount: this.entries.reduce((sum, entry) => sum + (entry.contrastFindings?.length ?? 0), 0),
            tests: this.entries,
        };

        fs.mkdirSync(path.dirname(this.outputFile), { recursive: true });
        fs.writeFileSync(this.outputFile, JSON.stringify(summary, null, 2), "utf-8");
    }
}
