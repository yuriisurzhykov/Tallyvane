#!/usr/bin/env node
// Run directly: node .cursor/lib/compact-output.test.mjs
import assert from "node:assert/strict";
import test from "node:test";
import {extractCompactFailures} from "./compact-output.mjs";

// Captured verbatim from a real `pnpm run lint` failure hit while building
// this harness (the eslint no-useless-assignment finding this hook's own
// first draft had) — not a constructed fixture.
const REAL_ESLINT_FAILURE = `E:\\Projects\\job-search-console\\.cursor\\hooks\\enforce-frontend-commands.mjs
  167:9  error  The value assigned to 'input' is not used in subsequent statements  no-useless-assignment

✖ 1 problem (1 error, 0 warnings)
`;

// Representative of tsc's default (non-pretty) diagnostic format.
const TSC_STYLE_FAILURE = `src/foo.ts(12,5): error TS2345: Argument of type 'string' is not assignable to parameter of type 'number'.
Found 1 error.
`;

// Representative of vitest's failure summary.
const VITEST_STYLE_FAILURE = `FAIL  src/foo.test.ts > MyComponent > renders correctly
AssertionError: expected 1 to be 2
 \u276F src/foo.test.ts:15:20
`;

test("extracts eslint's file-then-indented-finding shape from real output", () => {
    const {lines} = extractCompactFailures(REAL_ESLINT_FAILURE);
    assert.ok(
        lines.some((line) => line.includes("enforce-frontend-commands.mjs:167:9") && line.includes("no-useless-assignment")),
        `expected a line naming file:167:9, got: ${JSON.stringify(lines)}`,
    );
    assert.ok(lines.some((line) => line.includes("1 problem")));
});

test("extracts tsc's file(line,col): shape", () => {
    const {lines} = extractCompactFailures(TSC_STYLE_FAILURE);
    assert.ok(
        lines.some((line) => line.startsWith("src/foo.ts:12:5") && line.includes("TS2345")),
        `expected a line naming src/foo.ts:12:5, got: ${JSON.stringify(lines)}`,
    );
});

test("extracts vitest's file:line:col shape and keeps the surrounding failure lines", () => {
    const {lines} = extractCompactFailures(VITEST_STYLE_FAILURE);
    assert.ok(lines.some((line) => line.includes("FAIL") && line.includes("renders correctly")));
    assert.ok(lines.some((line) => line.includes("AssertionError")));
    assert.ok(lines.some((line) => line.includes("src/foo.test.ts:15:20")));
});

test("returns nothing for clean output", () => {
    const {lines, truncatedCount} = extractCompactFailures("All good, nothing to report.\nDone.\n");
    assert.deepEqual(lines, []);
    assert.equal(truncatedCount, 0);
});

test("deduplicates and caps at maxLines, reporting how many were dropped", () => {
    const repeated = Array.from({length: 50}, (_, i) => `src/foo.ts:${i}:1: error TS0: same message`).join("\n");
    const {lines, truncatedCount} = extractCompactFailures(repeated, {maxLines: 10});
    assert.equal(lines.length, 10);
    assert.equal(truncatedCount, 40);
});

test("handles non-string input without throwing", () => {
    const {lines, truncatedCount} = extractCompactFailures(undefined);
    assert.deepEqual(lines, []);
    assert.equal(truncatedCount, 0);
});
