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

/**
 * One row per suite, matching the suites themselves. A single "accessibility"
 * total would merge three different questions — is the markup sound, is the
 * contrast legal, is the contrast readable — and the answer to one says nothing
 * about the others.
 */
const lines = [
    `### ${failed ? "❌" : "✅"} Visual & accessibility tests`,
    "",
    "| | |",
    "|---|---|",
    `| Passed | ${summary.counts.passed} |`,
    `| Failed | ${summary.counts.failed} |`,
    `| Skipped | ${summary.counts.skipped} |`,
    `| Structural a11y violations | ${summary.a11yViolationCount} |`,
    `| Contrast, WCAG 2.2 AA — elements | ${summary.wcagContrastNodeCount} |`,
    `| Contrast, WCAG 2.2 AA — unmeasurable | ${summary.wcagContrastUnmeasuredCount} |`,
    `| Contrast, APCA — findings | ${summary.apcaFindingCount} |`,
    "",
    `[Full report, including visual diffs →](${runUrl}) — download the \`playwright-report\` artifact.`,
];

/** The full path repeats the project, the file and the section heading. Only the last segment says anything the reader does not already know. */
function shortTitle(title) {
    return title.split(" > ").at(-1) ?? title;
}

function axeLines(pick) {
    return summary.tests.flatMap((test) =>
        (pick(test) ?? []).map(
            (violation) =>
                `- \`${violation.id}\` — ${violation.nodes} element${violation.nodes === 1 ? "" : "s"}` +
                ` in _${shortTitle(test.title)}_ (${violation.impact})`,
        ),
    );
}

const structural = axeLines((test) => test.a11yViolations);
if (structural.length > 0) {
    lines.push("", "<details><summary>Structural accessibility</summary>", "", ...structural, "", "</details>");
}

const wcagContrast = axeLines((test) => test.wcagContrastViolations);
if (wcagContrast.length > 0) {
    lines.push(
        "",
        "<details><summary>Contrast — WCAG 2.2 AA</summary>",
        "",
        "This is the bar that is legally enforceable: the ADA, Section 508 and the European",
        "Accessibility Act all reference WCAG 2.x. Per-element detail is in the report artifact.",
        "",
        ...wcagContrast,
        "",
        "</details>",
    );
}

/**
 * Grouped by colour pair, not listed per element.
 *
 * One badly chosen role appears in every place it is used, so the raw list is
 * hundreds of lines describing a handful of mistakes — and reads as if the page
 * were hopeless rather than as if three colours need adjusting. Grouping turns
 * it back into the number of decisions to make.
 *
 * The worst case within a group is what gets reported: fixing that one fixes
 * the rest by construction, since they share the pair.
 */
const CONTRAST_SHOWN = 12;

const byPair = new Map();
for (const test of summary.tests) {
    for (const finding of test.contrastFindings ?? []) {
        // Grouped by pair AND by the level demanded, because the same two
        // colours can be fine as a heading and fail as body copy — those are
        // two different decisions, not one.
        const key = `${finding.foreground}|${finding.background}|${finding.requiredLc}|${shortTitle(test.title)}`;
        const group = byPair.get(key);
        if (group) {
            group.count += 1;
            group.styles.add(finding.style ?? "unclassified");
        } else {
            byPair.set(key, {
                finding,
                theme: shortTitle(test.title),
                count: 1,
                styles: new Set([finding.style ?? "unclassified"]),
            });
        }
    }
}

const contrast = [...byPair.values()]
    // Furthest from its target first: that is the order to fix them in.
    .sort((a, b) => a.finding.lc - a.finding.requiredLc - (b.finding.lc - b.finding.requiredLc))
    .map(({ finding, theme, count, styles }) =>
        `- \`${finding.foreground}\` on \`${finding.background}\`` +
        ` — Lc ${finding.lc}, needs Lc ${finding.requiredLc}` +
        ` · ${[...styles].join(", ")} · ${count} place${count === 1 ? "" : "s"} in _${theme}_`,
    );

if (contrast.length > 0) {
    lines.push(
        "",
        "<details><summary>Contrast — APCA</summary>",
        "",
        "A quality bar rather than a legal one, and it has no single pass mark: the level a piece of",
        "text is held to depends on what the text is for. Body copy is held to Lc 90, headings to 45,",
        "and text that is only meant to be glanced at — placeholders, disabled controls — to 30.",
        "",
        "Grouped by colour pair and required level: one group is one decision, however many places it",
        "appears in. Furthest from its target first.",
        "",
        ...contrast.slice(0, CONTRAST_SHOWN),
    );
    if (contrast.length > CONTRAST_SHOWN) {
        lines.push("", `_…and ${contrast.length - CONTRAST_SHOWN} more pairs. Per-element detail is in the report artifact._`);
    }
    lines.push("", "</details>");
}

fs.writeFileSync(outputPath, `${lines.join("\n")}\n`);
