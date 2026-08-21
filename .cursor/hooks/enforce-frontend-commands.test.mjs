#!/usr/bin/env node
// Run directly: node .cursor/hooks/enforce-frontend-commands.test.mjs
import assert from "node:assert/strict";
import test from "node:test";
import {evaluateCommand} from "./enforce-frontend-commands.mjs";

const ALLOWED = [
  "node .cursor/cli/agent-check.mjs typecheck",
  "node .cursor/cli/agent-check.mjs test:e2e --package frontend-web",
  "node .cursor/cli/agent-check.mjs verify",
  "node .cursor/cli/agent-check.mjs build --package frontend-admin",
    // Package-manager commands that are not, in fact, running a controlled
    // script — the exact false-positive class a naive substring/word-boundary
    // match would fall into.
    "pnpm why lint-staged",
    "pnpm add -D eslint-plugin-foo",
    "pnpm list --depth -1",
    "pnpm install --frozen-lockfile",
    "npm view some-package",
    "yarn info some-package",
    "npx eslint src/foo.ts",
    "npx tsc --noEmit --project tsconfig.check.json --listFiles",
    "npx playwright test frontend-web/tests/e2e/one.spec.ts",
    "git status",
    "ls -la",
];

const DENIED = [
    // Every shorthand a package manager actually supports for running a
    // controlled script directly, correctly formed or not.
    "pnpm run typecheck",
    "pnpm typecheck",
    "pnpm --filter \"./frontend-web\" run test:e2e",
    "pnpm --filter \"./frontend-web\" test:e2e",
    "pnpm --filter tallyvane-frontend-web run test:a11y",
    "pnpm --filter frontend-web run build",
    "npm run build",
    "npm test",
    "yarn test",
    "yarn typecheck",
    "yarn run lint",
    "cd frontend-web && pnpm run build",
    "cd packages/storybook; pnpm run test:e2e",
    // Bare direct-binary bypasses.
    "npx eslint",
    "npx tsc --noEmit",
    "npx playwright test",
    "node_modules/.bin/vitest run",
];

test("allows the wrapper in every form, and every pnpm/npm/yarn/npx command unrelated to a controlled action", () => {
    for (const command of ALLOWED) {
        const result = evaluateCommand(command);
        assert.equal(result.permission, "allow", `expected "${command}" to be allowed, got: ${JSON.stringify(result)}`);
    }
});

test("denies every direct invocation of a controlled action, correctly formed or not", () => {
    for (const command of DENIED) {
        const result = evaluateCommand(command);
        assert.equal(result.permission, "deny", `expected "${command}" to be denied, got: ${JSON.stringify(result)}`);
        assert.match(result.agent_message, /frontend-command-harness/);
        assert.match(result.agent_message, /agent-check\.mjs/);
    }
});

test("denial for a package-manager shorthand names the exact script, not a truncated prefix", () => {
    const result = evaluateCommand("pnpm run test:e2e");
    assert.match(result.agent_message, /run test:e2e\b/);
});

test("a compound command is denied if any segment runs a controlled script directly", () => {
    const result = evaluateCommand("node .cursor/cli/agent-check.mjs typecheck && pnpm run lint");
    assert.equal(result.permission, "deny");
});

test("empty or non-string input is allowed rather than throwing", () => {
    assert.equal(evaluateCommand("").permission, "allow");
    assert.equal(evaluateCommand(undefined).permission, "allow");
});
