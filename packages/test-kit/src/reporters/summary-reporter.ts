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
 * Two families of finding are digested. Anything axe reports — structural
 * problems and WCAG 2.2 AA contrast alike — arrives as a violation; APCA
 * contrast has its own shape and its own attachment, because it answers a
 * different question and comes from a different model.
 *
 * The APCA half is designed to be removable. Should that method be dropped, the
 * attachment simply stops appearing, `contrastFindings` is omitted and the count
 * stays at zero; deleting `CONTRAST_ATTACHMENT` and its two references then
 * tidies up. Nothing else here knows about it.
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
    /** The level this text is held to, which depends on what the text is for rather than on how big it is. */
    readonly requiredLc: number;
    /** The text style that decided the level, or `null` when nothing declared one. */
    readonly style: string | null;
    readonly fontSize: number;
    readonly fontWeight: number;
    /**
     * The colours carried through, so the summary can group by pair. Without
     * them the report is a list of selectors — and a page repeating one bad
     * role in ninety places produces ninety lines describing one mistake.
     */
    readonly foreground: string;
    readonly background: string;
}

interface TestSummaryEntry {
    readonly title: string;
    readonly project: string;
    readonly status: TestResult["status"];
    readonly durationMs: number;
    readonly errorMessage?: string;
    readonly a11yViolations?: readonly AxeViolationSummary[];
    readonly wcagContrastViolations?: readonly AxeViolationSummary[];
    /** Pairs axe could not compute. Counted apart from violations, because "unknown" and "wrong" call for different work. */
    readonly wcagContrastIncomplete?: readonly AxeViolationSummary[];
    readonly contrastFindings?: readonly ContrastFindingSummary[];
}

interface Summary {
    readonly generatedAt: string;
    readonly overallStatus: FullResult["status"];
    readonly counts: { readonly passed: number; readonly failed: number; readonly skipped: number; readonly total: number };
    readonly a11yViolationCount: number;
    /** Offending ELEMENTS, not rules: one rule failing on ninety nodes is ninety things to fix. */
    readonly wcagContrastNodeCount: number;
    readonly wcagContrastUnmeasuredCount: number;
    readonly apcaFindingCount: number;
    readonly tests: readonly TestSummaryEntry[];
}

const AXE_ATTACHMENT = "axe-results";
const WCAG_CONTRAST_ATTACHMENT = "wcag-contrast-results";
const APCA_ATTACHMENT = "apca-findings";

/** A malformed attachment yields nothing rather than throwing: a reporter that crashes takes the run's result with it, which is a worse outcome than an incomplete digest. */
function parseAttachment<T>(raw: string, map: (value: unknown) => T): T[] {
    try {
        const parsed: unknown = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed.map(map) : [];
    } catch {
        return [];
    }
}

/**
 * The contrast attachment carries `{ violations, incomplete }` rather than a
 * bare array, because a pair axe could not measure is a separate kind of news
 * from one it measured and rejected. Both are reported; neither is a pass.
 */
function parseAxeReport(raw: string): { violations: AxeViolationSummary[]; incomplete: AxeViolationSummary[] } {
    try {
        const parsed = JSON.parse(raw) as { violations?: unknown; incomplete?: unknown };
        const toList = (value: unknown) => (Array.isArray(value) ? value.map(toAxeSummary) : []);
        return { violations: toList(parsed.violations), incomplete: toList(parsed.incomplete) };
    } catch {
        return { violations: [], incomplete: [] };
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
    const finding = value as Partial<ContrastFindingSummary>;
    return {
        selector: finding.selector ?? "unknown",
        lc: finding.lc ?? 0,
        requiredLc: finding.requiredLc ?? 0,
        style: finding.style ?? null,
        fontSize: finding.fontSize ?? 0,
        fontWeight: finding.fontWeight ?? 400,
        foreground: finding.foreground ?? "unknown",
        background: finding.background ?? "unknown",
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

function countNodes(
    entries: readonly TestSummaryEntry[],
    pick: (entry: TestSummaryEntry) => readonly AxeViolationSummary[] | undefined,
): number {
    return entries.reduce((sum, entry) => sum + (pick(entry) ?? []).reduce((n, item) => n + item.nodes, 0), 0);
}

export default class SummaryReporter implements Reporter {
    private readonly entries: TestSummaryEntry[] = [];
    private readonly outputFile: string;

    public constructor(options: { readonly outputFile?: string } = {}) {
        this.outputFile = options.outputFile ?? path.join("test-results", "summary.json");
    }

    public onTestEnd(test: TestCase, result: TestResult): void {
        const axeRaw = readAttachment(result, AXE_ATTACHMENT);
        const wcagRaw = readAttachment(result, WCAG_CONTRAST_ATTACHMENT);
        const apcaRaw = readAttachment(result, APCA_ATTACHMENT);

        this.entries.push({
            title: test.titlePath().filter(Boolean).join(" > "),
            project: test.parent.project()?.name ?? "unknown",
            status: result.status,
            durationMs: result.duration,
            ...(result.status !== "passed" && result.errors[0]?.message
                ? { errorMessage: result.errors[0].message }
                : {}),
            ...(axeRaw ? { a11yViolations: parseAttachment(axeRaw, toAxeSummary) } : {}),
            ...(wcagRaw
                ? {
                    wcagContrastViolations: parseAxeReport(wcagRaw).violations,
                    wcagContrastIncomplete: parseAxeReport(wcagRaw).incomplete,
                }
                : {}),
            ...(apcaRaw ? { contrastFindings: parseAttachment(apcaRaw, toContrastSummary) } : {}),
        });
    }

    public onEnd(result: FullResult): void {
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
            wcagContrastNodeCount: countNodes(this.entries, (entry) => entry.wcagContrastViolations),
            wcagContrastUnmeasuredCount: countNodes(this.entries, (entry) => entry.wcagContrastIncomplete),
            apcaFindingCount: this.entries.reduce((sum, entry) => sum + (entry.contrastFindings?.length ?? 0), 0),
            tests: this.entries,
        };

        fs.mkdirSync(path.dirname(this.outputFile), { recursive: true });
        fs.writeFileSync(this.outputFile, JSON.stringify(summary, null, 2), "utf-8");
    }
}
