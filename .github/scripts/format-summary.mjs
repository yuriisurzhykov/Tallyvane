#!/usr/bin/env node
/**
 * Turns the Playwright summary reporter's `test-results/summary.json` into a
 * Markdown comment body.
 *
 * Written to a file rather than to `GITHUB_OUTPUT`, so the workflow can hand
 * the path straight to the sticky-comment action. Multiline values in step
 * outputs need escaping that fails in exactly the case that matters — a failure
 * message containing a newline.
 *
 * Two families of finding are reported, which is where this differs from the
 * usual version of this script. Structural accessibility comes from axe;
 * contrast does not, because this project measures with APCA rather than the
 * WCAG 2 ratio. A comment that counted only axe violations would announce a
 * clean run on a page whose text is unreadable.
 *
 * Usage: node format-summary.mjs <summaryJsonPath> <runUrl> <outputPath>
 */

import fs from "node:fs";

const [, , summaryPath, runUrl, outputPath] = process.argv;

if (!summaryPath || !runUrl || !outputPath) {
    console.error("Usage: format-summary.mjs <summaryJsonPath> <runUrl> <outputPath>");
    process.exit(1);
}

/**
 * A missing summary is itself the news: it means the suite never got far enough
 * to write one. Saying so beats posting nothing, which reads as "no problems".
 */
if (!fs.existsSync(summaryPath)) {
    fs.writeFileSync(
        outputPath,
        `### ⚠️ Visual & accessibility tests\n\nNo summary was produced — Playwright never finished. [See the run](${runUrl}).\n`,
    );
    process.exit(0);
}

const summary = JSON.parse(fs.readFileSync(summaryPath, "utf-8"));
const failed = summary.counts.failed > 0;

const lines = [
    `### ${failed ? "❌" : "✅"} Visual & accessibility tests`,
    "",
    "| | |",
    "|---|---|",
    `| Passed | ${summary.counts.passed} |`,
    `| Failed | ${summary.counts.failed} |`,
    `| Skipped | ${summary.counts.skipped} |`,
    `| Accessibility violations | ${summary.a11yViolationCount} |`,
    `| Contrast findings (APCA) | ${summary.contrastFindingCount} |`,
    "",
    `[Full report, including visual diffs →](${runUrl}) — download the \`playwright-report\` artifact.`,
];

const violations = summary.tests.flatMap((test) =>
    (test.a11yViolations ?? []).map(
        (violation) =>
            `- **[${violation.impact}]** \`${violation.id}\` in _${test.title}_ — ${violation.help}` +
            ` (${violation.nodes} element${violation.nodes === 1 ? "" : "s"})`,
    ),
);

if (violations.length > 0) {
    lines.push("", "<details><summary>Accessibility violations</summary>", "", ...violations, "", "</details>");
}

/**
 * Contrast findings are truncated. A theme-wide regression produces hundreds of
 * them, and a comment that long is scrolled past rather than read — the report
 * artifact holds the full list.
 */
const CONTRAST_SHOWN = 20;
const contrast = summary.tests.flatMap((test) =>
    (test.contrastFindings ?? []).map((finding) => {
        const need = finding.requiredSize >= 200
            ? "no size is sufficient at this weight"
            : `needs ${finding.requiredSize}px, is ${finding.fontSize}px`;
        return `- \`${finding.selector}\` in _${test.title}_ — Lc ${finding.lc}, ${need}`;
    }),
);

if (contrast.length > 0) {
    lines.push(
        "",
        "<details><summary>Contrast findings</summary>",
        "",
        "APCA has no single pass mark: the threshold depends on size and weight, so each line says",
        "what size the text would need at its measured contrast. A darker colour, a larger size or a",
        "heavier weight all fix it.",
        "",
        ...contrast.slice(0, CONTRAST_SHOWN),
    );
    if (contrast.length > CONTRAST_SHOWN) {
        lines.push("", `_…and ${contrast.length - CONTRAST_SHOWN} more. The full list is in the report artifact._`);
    }
    lines.push("", "</details>");
}

fs.writeFileSync(outputPath, `${lines.join("\n")}\n`);
