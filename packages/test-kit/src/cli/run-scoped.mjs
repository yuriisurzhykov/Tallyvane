#!/usr/bin/env node
/**
 * Wraps `playwright test` with three guardrails a bare `pnpm run test:*`
 * script does not give you, all aimed at the same problem: local iteration
 * on one story/component should not cost the same machine time and CPU as
 * the full suite CI runs on every PR.
 *
 * 1. Requires `-g <pattern>` (Playwright's own test-title filter) or an
 *    explicit `--all` — running everything by accident, because a flag was
 *    forgotten, is exactly the failure mode this script exists to close off.
 * 2. Defaults `--workers` to 2 rather than Playwright's own default (the
 *    CPU core count) — more workers means more simultaneous Chromium
 *    instances, which is the opposite of "go easy on this machine." Still
 *    overridable (`--workers=4`) for whoever wants the trade the other way.
 * 3. Frees `--ports` (this consumer's own dev-server ports) before AND
 *    after the run via `free-ports.mjs`, so a run that crashes or gets
 *    interrupted never leaves an orphaned server for the next one to trip
 *    over, and a leftover from an earlier ad-hoc debugging session gets
 *    cleared before this run even starts.
 *
 * Usage:
 *   node run-scoped.mjs --ports=6006,6007 contrast-apca.spec.ts -g meter
 *   node run-scoped.mjs --ports=6006,6007 --all a11y.spec.ts
 *
 * Everything not consumed by `--ports`/`--all` above is passed straight
 * through to `playwright test`.
 */
import { execFileSync, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import * as path from "node:path";

const FREE_PORTS_SCRIPT = path.join(path.dirname(fileURLToPath(import.meta.url)), "free-ports.mjs");

const rawArgs = process.argv.slice(2);
const portsArg = rawArgs.find((arg) => arg.startsWith("--ports="));
const runAll = rawArgs.includes("--all");
const passthroughArgs = rawArgs.filter((arg) => arg !== portsArg && arg !== "--all");

if (!portsArg) {
    console.error("Usage: node run-scoped.mjs --ports=<comma-separated> [--all] <playwright args...>");
    console.error("  --ports is required so this run's dev server(s) can be freed before and after.");
    process.exit(1);
}

const hasPattern = passthroughArgs.includes("-g") || passthroughArgs.includes("--grep");
if (!runAll && !hasPattern) {
    console.error("Refusing to run the full suite without asking first.");
    console.error("Pass -g <pattern> to scope this run, or --all to genuinely run everything.");
    console.error('Example: pnpm run test:scoped -- contrast-apca.spec.ts -g "meter"');
    process.exit(1);
}

const ports = portsArg.slice("--ports=".length).split(",");
const hasWorkers = passthroughArgs.some((arg) => arg === "--workers" || arg.startsWith("--workers="));
const playwrightArgs = ["test", ...passthroughArgs, ...(hasWorkers ? [] : ["--workers=2"])];

execFileSync(process.execPath, [FREE_PORTS_SCRIPT, ...ports], { stdio: "inherit" });

// `pnpm exec`, not a bare `playwright`: pnpm does not put workspace binaries on
// `PATH` itself, only into each package's own `node_modules/.bin` — `pnpm exec`
// is the one thing guaranteed to resolve `playwright` the same way `pnpm run`
// already does inside this script's caller.
const result = spawnSync("pnpm", ["exec", "playwright", ...playwrightArgs], {
    stdio: "inherit",
    shell: process.platform === "win32",
});

execFileSync(process.execPath, [FREE_PORTS_SCRIPT, ...ports], { stdio: "inherit" });

process.exit(result.status ?? 1);
