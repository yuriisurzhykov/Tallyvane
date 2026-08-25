#!/usr/bin/env node
/**
 * Rebuilds `storybook-static` only when a Storybook input is newer than the
 * last build. Playwright specs under `tests/` are not inputs — they run
 * against the already-built iframe, so a spec-only edit must not cost a
 * full `storybook build`. Storybook's own emit is all-or-nothing (there is
 * no per-story static rebuild), so "only build what changed" here means
 * skip vs full rebuild, not a partial compile. `*.md` / `*.test.*` /
 * `*.spec.*` under `frontend-shared` are ignored for the same reason.
 * Directory mtimes are inputs too: deleting or renaming a story updates
 * the parent folder and leaves no newer file. Workspace `pnpm-lock.yaml`
 * and the repo-root `package.json` are inputs so a lockfile-only bump of
 * Storybook/Tailwind/React still rebuilds (`node_modules` itself is skipped).
 *
 * CI always rebuilds: `storybook-static/` is gitignored and the visual
 * workflow does not cache it, but a future cache of that folder would
 * otherwise silently test stale HTML. `pnpm run build-storybook` still
 * force-rebuilds locally when you want that.
 */
import {spawnSync} from "node:child_process";
import {readdirSync, statSync} from "node:fs";
import {dirname, join, relative, resolve} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";

const PACKAGE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const OUTPUT_INDEX = join(PACKAGE_ROOT, "storybook-static", "index.json");

const SKIP_DIR_NAMES = new Set([
    "node_modules",
    ".git",
    "dist",
    "storybook-static",
    "test-results",
    "playwright-report",
]);

function isNonBuildFile(name) {
    return name.endsWith(".md") || /\.(?:test|spec)\.[cm]?[jt]sx?$/.test(name);
}

/**
 * Paths whose mtime can change the built iframe. Specs, snapshots and
 * Playwright config are deliberately absent — they do not feed `storybook
 * build`. The lockfile and repo-root `package.json` stand in for
 * `node_modules`, which this walk skips.
 */
export function inputRoots(packageRoot = PACKAGE_ROOT) {
    return [
        join(packageRoot, ".storybook"),
        join(packageRoot, "package.json"),
        join(packageRoot, "postcss.config.mjs"),
        join(packageRoot, "tsconfig.json"),
        join(packageRoot, "..", "frontend-shared", "src"),
        join(packageRoot, "..", "frontend-shared", "package.json"),
        join(packageRoot, "..", "..", "pnpm-lock.yaml"),
        join(packageRoot, "..", "..", "package.json"),
    ];
}

export function rebuildReason({ci, outputExists, stalePath}) {
    if (ci) {
        return "CI";
    }
    if (!outputExists) {
        return "missing storybook-static/index.json";
    }
    if (stalePath) {
        return `stale: ${stalePath}`;
    }
    return null;
}

export function findStaleInput(roots, outputMtimeMs) {
    for (const root of roots) {
        const stale = walkForStale(root, outputMtimeMs);
        if (stale !== null) {
            return stale;
        }
    }
    return null;
}

function walkForStale(absPath, outputMtimeMs) {
    let stat;
    try {
        stat = statSync(absPath);
    } catch (error) {
        if (error && error.code === "ENOENT") {
            return null;
        }
        throw error;
    }
    if (stat.isDirectory()) {
        for (const name of readdirSync(absPath)) {
            if (SKIP_DIR_NAMES.has(name) || isNonBuildFile(name)) {
                continue;
            }
            const found = walkForStale(join(absPath, name), outputMtimeMs);
            if (found !== null) {
                return found;
            }
        }
        // Deletion/rename updates the directory's mtime without leaving a newer
        // file behind, so the walk above would otherwise miss it.
        return stat.mtimeMs > outputMtimeMs ? absPath : null;
    }
    return stat.mtimeMs > outputMtimeMs ? absPath : null;
}

function outputStamp(outputIndex = OUTPUT_INDEX) {
    try {
        return {exists: true, mtimeMs: statSync(outputIndex).mtimeMs};
    } catch (error) {
        if (error && error.code === "ENOENT") {
            return {exists: false, mtimeMs: 0};
        }
        throw error;
    }
}

export function decideRebuild({
    ci = Boolean(process.env.CI),
    packageRoot = PACKAGE_ROOT,
    outputIndex = join(packageRoot, "storybook-static", "index.json"),
} = {}) {
    const stamp = outputStamp(outputIndex);
    const staleAbs = stamp.exists ? findStaleInput(inputRoots(packageRoot), stamp.mtimeMs) : null;
    const stalePath = staleAbs === null ? null : relative(packageRoot, staleAbs);
    return rebuildReason({ci, outputExists: stamp.exists, stalePath});
}

function runBuild() {
    const result = spawnSync("pnpm", ["run", "build-storybook"], {
        cwd: PACKAGE_ROOT,
        stdio: "inherit",
        shell: process.platform === "win32",
    });
    if (result.error) {
        process.stderr.write(`ensure-storybook-static: failed to spawn build-storybook: ${result.error.message}\n`);
        process.exit(1);
    }
    process.exit(result.status ?? 1);
}

function main() {
    const reason = decideRebuild();
    if (reason === null) {
        process.stdout.write("ensure-storybook-static: skipping storybook build (storybook-static is newer than inputs)\n");
        return;
    }
    process.stdout.write(`ensure-storybook-static: building storybook (${reason})\n`);
    runBuild();
}

const isMainModule = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
    main();
}
