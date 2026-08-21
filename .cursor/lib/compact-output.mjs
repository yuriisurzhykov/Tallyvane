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
 * first specifically so a heuristic miss is an inconvenience, never a loss.
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
// costs. Catches "AssertionError", "Failed to compile.", "1 error", etc.
const FAILURE_MARKER = /error|fail|✖|✗|×/i;

// A header that starts a multi-line failure block rather than being the
// whole failure itself — vitest's `FAIL  file > suite > test` line, or a
// thrown error's own `SomeError: message` first line. Real, captured
// output (see compact-output.test.mjs's WEIRD_VITEST_DIFF_FAILURE fixture)
// showed the naive single-line extraction below was silently dropping the
// entire expected/received diff that follows one of these — the single
// most useful part of the failure — because none of its lines look like a
// file reference or contain the word "error"/"fail" on their own.
const BLOCK_TRIGGER = /^\s*(FAIL\b|[A-Za-z][\w.]*Error:)/;

// A divider row a tool prints between sections (vitest's `⎯⎯⎯ Failed Tests
// ⎯⎯⎯`) — never part of the failure's own message, so it closes a block
// without being included in it.
const BLOCK_DIVIDER = /[⎯─=]{4,}/;

const MAX_BLOCK_LINES = 60;

function isBlockBoundary(line) {
  return BLOCK_DIVIDER.test(line) || BARE_FILE_PATH_LINE.test(line) || BLOCK_TRIGGER.test(line);
}

/** eslint "stylish" bare path line — the new current-file context for indented findings under it, or null. */
function matchBarePath(line) {
  const barePath = line.match(BARE_FILE_PATH_LINE);
  return barePath ? barePath[1] : null;
}

/** One `line:col  error  message` row indented under a bare path line already seen. */
function matchIndentedFinding(line, currentFile) {
  if (!currentFile) return null;
  const indented = line.match(INDENTED_LINE_COL);
  if (!indented) return null;
  const [, lineNo, col, severity, message] = indented;
  return `${currentFile}:${lineNo}:${col} — ${severity}: ${message.trim()}`;
}

/** A file reference with its line:col already inline (tsc/vitest/playwright/depcruise/next's own shape). */
function matchInlineLocation(line) {
  const inline = line.match(INLINE_FILE_LINE_COL);
  if (!inline) return null;
  const [, file, parenLine, parenCol, colonLine, colonCol] = inline;
  const lineNo = parenLine ?? colonLine;
  const col = parenCol ?? colonCol;
  const location = col ? `${file}:${lineNo}:${col}` : `${file}:${lineNo}`;
  const rest = line
    .slice(inline.index + inline[0].length)
    .replace(/^[:)]\s*/, "")
    .trim();
  return rest ? `${location} — ${rest}` : location;
}

/**
 * Captures a `BLOCK_TRIGGER` header plus every following line verbatim — a
 * diff, a stack trace, whatever the tool put there — until a divider, a new
 * file block, another trigger, a location pointer that names where it
 * happened, or the safety cap closes it.
 */
function captureBlock(lines, startIndex) {
  const block = [lines[startIndex].trim()];
  let j = startIndex + 1;
  while (j < lines.length && block.length < MAX_BLOCK_LINES) {
    const next = lines[j];
    if (isBlockBoundary(next)) break;
    block.push(next);
    j += 1;
    if (INLINE_FILE_LINE_COL.test(next)) break;
  }
  return { text: block.join("\n").trim(), nextIndex: j };
}

export function extractCompactFailures(rawOutput, { maxLines = 40 } = {}) {
  const lines = typeof rawOutput === "string" ? rawOutput.split(/\r?\n/) : [];
  const found = [];
  let currentFile = null;
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    const barePath = matchBarePath(line);
    if (barePath) {
      currentFile = barePath;
      i += 1;
      continue;
    }

    const indentedFinding = matchIndentedFinding(line, currentFile);
    if (indentedFinding) {
      found.push(indentedFinding);
      i += 1;
      continue;
    }

    const inlineLocation = matchInlineLocation(line);
    if (inlineLocation) {
      found.push(inlineLocation);
      i += 1;
      continue;
    }

    if (BLOCK_TRIGGER.test(line)) {
      const { text, nextIndex } = captureBlock(lines, i);
      found.push(text);
      i = nextIndex;
      continue;
    }

    if (line.trim() !== "" && FAILURE_MARKER.test(line)) {
      found.push(line.trim());
    }
    i += 1;
  }

  const deduped = [...new Set(found)];
  const truncatedCount = Math.max(0, deduped.length - maxLines);
  return {
    lines: truncatedCount > 0 ? deduped.slice(0, maxLines) : deduped,
    truncatedCount,
  };
}
