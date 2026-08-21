#!/usr/bin/env node
// Run directly: node .cursor/lib/compact-output.test.mjs
//
// Every REAL_* fixture below is captured verbatim from an actual run against
// this repository (via `node .cursor/cli/agent-check.mjs <action> --package
// <path>` on a deliberately broken scratch file, then deleted) — not
// constructed by hand. That's on purpose: this extractor's job is to survive
// contact with what these tools actually print, not what they're documented
// to print.
import assert from "node:assert/strict";
import test from "node:test";
import { extractCompactFailures } from "./compact-output.mjs";

// `pnpm --filter "./packages/frontend-shared" run typecheck` against a
// single-finding scratch file.
const REAL_ESLINT_SINGLE_FAILURE = `E:\\Projects\\job-search-console\\.cursor\\hooks\\enforce-frontend-commands.mjs
  167:9  error  The value assigned to 'input' is not used in subsequent statements  no-useless-assignment

✖ 1 problem (1 error, 0 warnings)
`;

// `pnpm run lint` against two scratch files with two findings each — real
// multi-error, multi-file eslint "stylish" output.
const REAL_ESLINT_MULTIPLE_FAILURES = `E:\\Projects\\job-search-console\\packages\\frontend-shared\\src\\shared\\lib\\__scratch-lint-a.ts
  2:7  error  'unusedLocal' is never reassigned. Use 'const' instead                                  prefer-const
  2:7  error  'unusedLocal' is assigned a value but never used. Allowed unused vars must match /^_/u  @typescript-eslint/no-unused-vars

E:\\Projects\\job-search-console\\packages\\frontend-shared\\src\\shared\\lib\\__scratch-lint-b.ts
  2:7  error  The value assigned to 'result' is not used in subsequent statements  no-useless-assignment
  4:5  error  The value assigned to 'result' is not used in subsequent statements  no-useless-assignment

✖ 4 problems (4 errors, 0 warnings)
  1 error and 0 warnings potentially fixable with the \`--fix\` option.
`;

// `pnpm --filter "./packages/frontend-shared" run typecheck` against a
// scratch file with four independent, simultaneous type errors (three plain
// mismatches plus one "missing properties" elaboration).
const REAL_TSC_MULTIPLE_FAILURES = `src/shared/lib/__scratch-multi-errors.ts(1,7): error TS2322: Type 'string' is not assignable to type 'number'.
src/shared/lib/__scratch-multi-errors.ts(2,7): error TS2322: Type 'number' is not assignable to type 'string'.
src/shared/lib/__scratch-multi-errors.ts(3,7): error TS2322: Type 'string' is not assignable to type 'boolean'.
src/shared/lib/__scratch-multi-errors.ts(13,7): error TS2739: Type '{ alpha: string; }' is missing the following properties from type 'StrictShape': beta, gamma, delta, epsilon
E:\\Projects\\job-search-console\\packages\\frontend-shared:
[ERR_PNPM_RECURSIVE_RUN_FIRST_FAIL] frontend-shared@0.0.0 typecheck: \`tsc --noEmit\`
Exit status 2

$ tsc --noEmit
`;

// `pnpm --filter "./packages/frontend-shared" run test` against a scratch
// vitest test that compares two differently-shaped objects — the "weird"
// case: the actually useful part (the expected/received diff) is a block of
// plain +/- lines that don't individually look like a file reference or
// contain the word "error"/"fail" at all. A first version of this extractor
// silently dropped the entire diff, keeping only vitest's own one-line,
// already-truncated summary ("expected { Object (alpha, beta, ...) } to
// deeply equal { Object (alpha, beta, ...) }") — this fixture exists so that
// regression can never land silently again.
const REAL_VITEST_WEIRD_DIFF_FAILURE = `
 RUN  v4.1.10 E:/Projects/job-search-console/packages/frontend-shared

 ❯ src/shared/lib/__scratch-weird.test.ts (1 test | 1 failed) 14ms
     × compares two differently-shaped objects 12ms

 Test Files  1 failed | 69 passed (70)
      Tests  1 failed | 641 passed | 2 skipped (644)
   Start at  23:13:25
   Duration  42.79s

E:\\Projects\\job-search-console\\packages\\frontend-shared:
[ERR_PNPM_RECURSIVE_RUN_FIRST_FAIL] frontend-shared@0.0.0 test: \`vitest run\`
Exit status 1

$ vitest run

⎯⎯⎯⎯⎯⎯⎯ Failed Tests 1 ⎯⎯⎯⎯⎯⎯⎯

 FAIL  src/shared/lib/__scratch-weird.test.ts > scratch weird failure > compares two differently-shaped objects
AssertionError: expected { Object (alpha, beta, ...) } to deeply equal { Object (alpha, beta, ...) }

- Expected
+ Received

  {
-   "alpha": 2,
+   "alpha": 1,
    "beta": {
      "deep": [
-       4,
-       5,
-       6,
+       1,
+       2,
+       3,
      ],
-     "nested": "two",
+     "nested": "one",
    },
-   "gamma": "y",
+   "gamma": "x",
  }

 ❯ src/shared/lib/__scratch-weird.test.ts:7:20
      5|     const actual = { alpha: 1, beta: { nested: "one", deep: [1, 2, 3] …
      6|     const expected = { alpha: 2, beta: { nested: "two", deep: [4, 5, 6…
      7|     expect(actual).toEqual(expected);
       |                    ^
      8|   });
      9| });

⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[1/1]⎯

`;

// `pnpm --filter "./frontend-web" run arch` on a clean tree — real success
// output, deliberately chosen because it's full of characters a naive
// "contains a failure word" check could misfire on (✔, √, "No problems").
const REAL_CLEAN_ARCH_OUTPUT = `[tokens] Generated files match the source.

✔ no dependency violations found (72 modules, 135 dependencies cruised)


$ pnpm run tokens:check && pnpm run arch:fsd && pnpm run arch:graph
$ tsx scripts/generate-design-tokens.ts --check
$ steiger ./src
√ No problems found!
$ depcruise app src scripts --config .dependency-cruiser.cjs
`;

test("1. multiple simultaneous errors: every one of four real tsc errors is extracted, none dropped", () => {
  const { lines } = extractCompactFailures(REAL_TSC_MULTIPLE_FAILURES);
  assert.ok(lines.some((l) => l.includes(":1:7") && l.includes("TS2322") && l.includes("not assignable to type 'number'")));
  assert.ok(lines.some((l) => l.includes(":2:7") && l.includes("TS2322") && l.includes("not assignable to type 'string'")));
  assert.ok(lines.some((l) => l.includes(":3:7") && l.includes("TS2322") && l.includes("not assignable to type 'boolean'")));
  assert.ok(lines.some((l) => l.includes(":13:7") && l.includes("TS2739") && l.includes("beta, gamma, delta, epsilon")));
});

test("1. multiple simultaneous errors: every one of four real eslint findings across two files is extracted", () => {
  const { lines } = extractCompactFailures(REAL_ESLINT_MULTIPLE_FAILURES);
  assert.ok(lines.some((l) => l.includes("__scratch-lint-a.ts:2:7") && l.includes("prefer-const")));
  assert.ok(lines.some((l) => l.includes("__scratch-lint-a.ts:2:7") && l.includes("no-unused-vars")));
  assert.ok(lines.some((l) => l.includes("__scratch-lint-b.ts:2:7") && l.includes("no-useless-assignment")));
  assert.ok(lines.some((l) => l.includes("__scratch-lint-b.ts:4:5") && l.includes("no-useless-assignment")));
  assert.ok(lines.some((l) => l.includes("4 problems")));
});

test("2. no errors at all: a real clean run produces zero compact lines, even with ✔/√ and 'No problems'", () => {
  const { lines, truncatedCount } = extractCompactFailures(REAL_CLEAN_ARCH_OUTPUT);
  assert.deepEqual(lines, []);
  assert.equal(truncatedCount, 0);
});

test("2. no errors at all: real empty tsc success output (nothing printed at all)", () => {
  const { lines } = extractCompactFailures("");
  assert.deepEqual(lines, []);
});

test("3. a weird error: the full expected/received diff body is captured, not just vitest's own truncated summary", () => {
  const { lines } = extractCompactFailures(REAL_VITEST_WEIRD_DIFF_FAILURE);
  const joined = lines.join("\n");
  // The one-line summary alone is exactly what the pre-fix version kept —
  // asserting on it isn't enough, the diff body is the point of this test.
  assert.match(joined, /AssertionError: expected \{ Object/);
  // Every line of the actual diff, both sides, has to survive.
  assert.match(joined, /-\s+"alpha": 2,/);
  assert.match(joined, /\+\s+"alpha": 1,/);
  assert.match(joined, /-\s+"nested": "two",/);
  assert.match(joined, /\+\s+"nested": "one",/);
  assert.match(joined, /-\s+4,/);
  assert.match(joined, /\+\s+1,/);
  assert.match(joined, /-\s+"gamma": "y",/);
  assert.match(joined, /\+\s+"gamma": "x",/);
  // And the location the failure points back to.
  assert.match(joined, /__scratch-weird\.test\.ts:7:20/);
});

test("3. a weird error: the diff and its location land in the same block, not scattered across unrelated entries", () => {
  const { lines } = extractCompactFailures(REAL_VITEST_WEIRD_DIFF_FAILURE);
  const diffBlock = lines.find((l) => l.includes("AssertionError"));
  assert.ok(diffBlock, "expected one entry containing the AssertionError header");
  assert.match(diffBlock, /"alpha": 2/);
  assert.match(diffBlock, /__scratch-weird\.test\.ts:7:20/);
});

test("extracts eslint's file-then-indented-finding shape from real single-failure output", () => {
  const { lines } = extractCompactFailures(REAL_ESLINT_SINGLE_FAILURE);
  assert.ok(lines.some((line) => line.includes("enforce-frontend-commands.mjs:167:9") && line.includes("no-useless-assignment")));
  assert.ok(lines.some((line) => line.includes("1 problem")));
});

test("deduplicates and caps at maxLines, reporting how many were dropped", () => {
  const repeated = Array.from({ length: 50 }, (_, i) => `src/foo.ts:${i}:1: error TS0: same message`).join("\n");
  const { lines, truncatedCount } = extractCompactFailures(repeated, { maxLines: 10 });
  assert.equal(lines.length, 10);
  assert.equal(truncatedCount, 40);
});

test("handles non-string input without throwing", () => {
  const { lines, truncatedCount } = extractCompactFailures(undefined);
  assert.deepEqual(lines, []);
  assert.equal(truncatedCount, 0);
});
