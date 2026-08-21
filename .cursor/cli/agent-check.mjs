#!/usr/bin/env node
/**
 * The one script agents run for every build/test/lint/check action across
 * frontend-web, frontend-admin and packages/* — see
 * .cursor/rules/frontend-command-harness.mdc for why this exists instead of
 * calling pnpm directly. `.cursor/hooks/enforce-frontend-commands.mjs`
 * denies the direct form, so this is not merely the recommended path, it is
 * the only one that runs.
 *
 * Usage:
 *   node .cursor/cli/agent-check.mjs <action> [--package <path>] [-- <extra args>]
 *   node .cursor/cli/agent-check.mjs --help
 *
 * On success: a single "OK <action>" line, nothing else — the full output
 * still exists on disk (see below) but isn't worth spending context on.
 * On failure: a handful of compact "file:line — message" lines extracted
 * from the tool's real output, plus a pointer to the full log. Never the
 * raw, unfiltered output of the underlying tool directly on stdout.
 */
import {spawnSync} from "node:child_process";
import {mkdirSync, writeFileSync} from "node:fs";
import {dirname, join, resolve} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";
import {buildPnpmArgs, CONTROLLED_ACTION_NAMES, CONTROLLED_ACTIONS, findAction} from "../lib/actions.mjs";
import {extractCompactFailures} from "../lib/compact-output.mjs";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const LOG_DIR = join(REPO_ROOT, ".cursor", "agent-check-logs");
const MAX_COMPACT_LINES = 40;
const MAX_FALLBACK_TAIL_LINES = 20;

function printUsage() {
    const rows = CONTROLLED_ACTIONS.map((action) => `  ${action.name.padEnd(20)} ${action.scope}`).join("\n");
    process.stdout.write(
        [
            "Usage: node .cursor/cli/agent-check.mjs <action> [--package <path>] [-- <extra args>]",
            "",
            "scope legend:",
            "  repo-only        no --package: only the root defines this",
            "  repo-or-package  either the whole repo, or one --package",
            "  package-only     requires --package",
            "",
            "actions:",
            rows,
            "",
        ].join("\n"),
    );
}

export function parseArgs(argv) {
    if (argv.length === 0 || argv[0] === "--help" || argv[0] === "-h") {
        return {help: true};
    }
    const [action, ...rest] = argv;
    let packagePath;
    const passthrough = [];
    for (let i = 0; i < rest.length; i += 1) {
        const arg = rest[i];
        if (arg === "--package") {
            packagePath = rest[i + 1];
            i += 1;
        } else if (arg.startsWith("--package=")) {
            packagePath = arg.slice("--package=".length);
        } else if (arg === "--") {
            passthrough.push(...rest.slice(i + 1));
            break;
        } else {
            passthrough.push(arg);
        }
    }
    return {action, packagePath, passthrough};
}

/**
 * Pure: returns either `{ action }` or `{ error }`, never writes to a stream
 * or touches `process`, so it's testable as plain input/output.
 */
export function validate({action: actionName, packagePath}) {
    const action = findAction(actionName);
    if (!action) {
        return {error: `unknown action "${actionName}". Valid actions: ${CONTROLLED_ACTION_NAMES.join(", ")}`};
    }
    if (action.scope === "repo-only" && packagePath) {
        return {error: `"${action.name}" has no per-package form — only the root defines it. Run it without --package.`};
    }
    if (action.scope === "package-only" && !packagePath) {
        return {error: `"${action.name}" requires --package <path> — there is no repo-wide form.`};
    }
    return {action};
}

function sanitizeForFilename(value) {
    return value.replace(/[^a-zA-Z0-9_.-]/g, "_");
}

function writeLog({action, packagePath, pnpmArgs, result}) {
    mkdirSync(LOG_DIR, {recursive: true});
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    const suffix = packagePath ? `-${sanitizeForFilename(packagePath)}` : "";
    const logPath = join(LOG_DIR, `${stamp}-${sanitizeForFilename(action)}${suffix}.log`);
    const header = [`command: pnpm ${pnpmArgs.join(" ")}`, `cwd: ${REPO_ROOT}`, `exit code: ${result.status}`, "---"].join("\n");
    writeFileSync(logPath, `${header}\n${result.stdout ?? ""}\n${result.stderr ?? ""}\n`, "utf8");
    return logPath;
}

function reportFailure({combinedOutput, logPath, exitStatus}) {
    const {lines, truncatedCount} = extractCompactFailures(combinedOutput, {maxLines: MAX_COMPACT_LINES});
    if (lines.length > 0) {
        process.stdout.write(`${lines.join("\n")}\n`);
        if (truncatedCount > 0) {
            process.stdout.write(`… ${truncatedCount} more, see log\n`);
        }
    } else {
        // The heuristic recognized nothing — most tools print their real
        // summary last, so the tail is the best untargeted guess.
        const tail = combinedOutput.split(/\r?\n/).filter((line) => line.trim() !== "").slice(-MAX_FALLBACK_TAIL_LINES);
        process.stdout.write(`${tail.join("\n")}\n`);
    }
    process.stdout.write(`FAILED (exit ${exitStatus}). Full output: ${logPath}\n`);
}

function main() {
    const parsed = parseArgs(process.argv.slice(2));
    if (parsed.help) {
        printUsage();
        process.exit(0);
    }

    const {action, error} = validate(parsed);
    if (error) {
        process.stderr.write(`agent-check: ${error}\n`);
        printUsage();
        process.exit(2);
    }

    const pnpmArgs = buildPnpmArgs(action.name, parsed.packagePath);
    if (parsed.passthrough.length > 0) {
        pnpmArgs.push("--", ...parsed.passthrough);
    }

    const result = spawnSync("pnpm", pnpmArgs, {
        cwd: REPO_ROOT,
        shell: true,
        encoding: "utf8",
    });

    if (result.error) {
        process.stderr.write(`agent-check: failed to run pnpm: ${result.error.message}\n`);
        process.exit(1);
    }

    const logPath = writeLog({action: action.name, packagePath: parsed.packagePath, pnpmArgs, result});

    if (result.status === 0) {
        process.stdout.write(`OK ${action.name}${parsed.packagePath ? ` (${parsed.packagePath})` : ""}\n`);
        process.exit(0);
    }

    reportFailure({
        combinedOutput: `${result.stdout ?? ""}\n${result.stderr ?? ""}`,
        logPath,
        exitStatus: result.status,
    });
    process.exit(result.status ?? 1);
}

const isMainModule = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
    main();
}
