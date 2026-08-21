/**
 * Reduces a failing tool's raw output (tsc, eslint, vitest, playwright,
 * depcruise, steiger, next build — each with its own format) down to the
 * lines that actually say what broke and where, so a failing check costs a
 * handful of lines of agent context instead of the full raw log.
 *
 * Heuristic, not a real parser for five different tools' output grammars —
 * deliberately: exhaustively parsing each one is far more code than the
 * problem justifies, and this only ever needs to be "good enough that the
 * agent doesn't need the full log," not "byte-perfect." The caller
 * (agent-check.mjs) always writes the untouched raw output to a log file
 * first specifically so a heuristic miss is a inconvenience, never a loss.
 */

// A bare file path on its own line — eslint's "stylish" formatter's own
// convention: the path once, then one indented line per finding under it.
const BARE_FILE_PATH_LINE = /^\s*((?:\.{1,2}[\\/])?(?:[A-Za-z]:)?[^\s:()]+\.(?:tsx?|jsx?|mjs|cjs|mts|cts|kt))\s*$/;

// One eslint finding, indented under the bare path line above it.
const INDENTED_LINE_COL = /^\s*(\d+):(\d+)\s+(error|warning)\s+(.+)$/;

// A file reference with its line:col already inline — tsc's `file(l,c):`,
// or the `file:l:c` shape vitest/playwright/depcruise/next all use.
const INLINE_FILE_LINE_COL = /([^\s:()"']+\.(?:tsx?|jsx?|mjs|cjs|mts|cts|kt))(?:\((\d+),(\d+)\)|:(\d+)(?::(\d+))?)/;

// Deliberately loose (substring, case-insensitive, no word boundaries): a
// missed real failure line costs far more than one extra harmless line
// costs. Catches "AssertionError", "Failed to compile", "1 error", etc.
const FAILURE_MARKER = /error|fail|✖|✗|×/i;

export function extractCompactFailures(rawOutput, {maxLines = 40} = {}) {
    const lines = typeof rawOutput === "string" ? rawOutput.split(/\r?\n/) : [];
    const found = [];
    let currentFile = null;

    for (const line of lines) {
        const barePath = line.match(BARE_FILE_PATH_LINE);
        if (barePath) {
            currentFile = barePath[1];
            continue;
        }

        const indented = currentFile && line.match(INDENTED_LINE_COL);
        if (indented) {
            const [, lineNo, col, severity, message] = indented;
            found.push(`${currentFile}:${lineNo}:${col} — ${severity}: ${message.trim()}`);
            continue;
        }

        const inline = line.match(INLINE_FILE_LINE_COL);
        if (inline) {
            const [, file, parenLine, parenCol, colonLine, colonCol] = inline;
            const lineNo = parenLine ?? colonLine;
            const col = parenCol ?? colonCol;
            const location = col ? `${file}:${lineNo}:${col}` : `${file}:${lineNo}`;
            const rest = line
                .slice(inline.index + inline[0].length)
                .replace(/^[:)]\s*/, "")
                .trim();
            found.push(rest ? `${location} — ${rest}` : location);
            continue;
        }

        if (line.trim() !== "" && FAILURE_MARKER.test(line)) {
            found.push(line.trim());
        }
    }

    const deduped = [...new Set(found)];
    const truncatedCount = Math.max(0, deduped.length - maxLines);
    return {
        lines: truncatedCount > 0 ? deduped.slice(0, maxLines) : deduped,
        truncatedCount,
    };
}
