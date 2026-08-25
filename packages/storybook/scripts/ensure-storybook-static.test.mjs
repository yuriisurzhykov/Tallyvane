#!/usr/bin/env node
// Run directly: node packages/storybook/scripts/ensure-storybook-static.test.mjs
import assert from "node:assert/strict";
import {mkdir, mkdtemp, rm, utimes, writeFile} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import test from "node:test";
import {fileURLToPath} from "node:url";
import {decideRebuild, findStaleInput, inputRoots, rebuildReason} from "./ensure-storybook-static.mjs";

const PACKAGE_ROOT = fileURLToPath(new URL("..", import.meta.url));

test("rebuildReason: CI always rebuilds, even with a fresh stamp", () => {
    assert.equal(rebuildReason({ci: true, outputExists: true, stalePath: null}), "CI");
});

test("rebuildReason: missing output rebuilds; matching stamp skips", () => {
    assert.equal(rebuildReason({ci: false, outputExists: false, stalePath: null}), "missing storybook-static/index.json");
    assert.equal(rebuildReason({ci: false, outputExists: true, stalePath: null}), null);
    assert.equal(
        rebuildReason({ci: false, outputExists: true, stalePath: "../frontend-shared/src/shared/ui/menu/Menu.tsx"}),
        "stale: ../frontend-shared/src/shared/ui/menu/Menu.tsx",
    );
});

test("inputRoots: Storybook config, frontend-shared source, and workspace lockfile", () => {
    const roots = inputRoots(PACKAGE_ROOT);
    assert.ok(roots.includes(join(PACKAGE_ROOT, ".storybook")));
    assert.ok(roots.includes(join(PACKAGE_ROOT, "..", "frontend-shared", "src")));
    assert.ok(roots.includes(join(PACKAGE_ROOT, "..", "..", "pnpm-lock.yaml")));
    assert.ok(roots.includes(join(PACKAGE_ROOT, "..", "..", "package.json")));
    assert.ok(!roots.some((root) => root.includes(`${join("tests", "e2e")}`)));
});

test("findStaleInput: a newer file under an input root is reported; an older one is not", async () => {
    const dir = await mkdtemp(join(tmpdir(), "ensure-storybook-static-"));
    try {
        const inputFile = join(dir, "Menu.tsx");
        await writeFile(inputFile, "export const x = 1;\n");
        const stampMs = Date.now();
        const older = new Date(stampMs - 60_000);
        const newer = new Date(stampMs + 60_000);

        await utimes(inputFile, older, older);
        await utimes(dir, older, older);
        assert.equal(findStaleInput([dir], stampMs), null);

        await utimes(inputFile, newer, newer);
        assert.equal(findStaleInput([dir], stampMs), inputFile);
    } finally {
        await rm(dir, {recursive: true, force: true});
    }
});

test("findStaleInput: README and *.test.tsx edits do not count as Storybook inputs", async () => {
    const dir = await mkdtemp(join(tmpdir(), "ensure-storybook-static-"));
    try {
        const readme = join(dir, "README.md");
        const unit = join(dir, "Menu.test.tsx");
        await writeFile(readme, "# menu\n");
        await writeFile(unit, "it('noop', () => {});\n");
        const newer = new Date(Date.now() + 60_000);
        const older = new Date(Date.now() - 60_000);
        await utimes(readme, newer, newer);
        await utimes(unit, newer, newer);
        await utimes(dir, older, older);
        assert.equal(findStaleInput([dir], Date.now()), null);
    } finally {
        await rm(dir, {recursive: true, force: true});
    }
});

test("findStaleInput: deleting a build file is stale via the directory mtime", async () => {
    const dir = await mkdtemp(join(tmpdir(), "ensure-storybook-static-"));
    try {
        const inputFile = join(dir, "Menu.tsx");
        await writeFile(inputFile, "export const x = 1;\n");
        const stampMs = Date.now();
        const older = new Date(stampMs - 60_000);
        const newer = new Date(stampMs + 60_000);
        await utimes(inputFile, older, older);
        await utimes(dir, older, older);
        assert.equal(findStaleInput([dir], stampMs), null);

        await rm(inputFile);
        await utimes(dir, newer, newer);
        assert.equal(findStaleInput([dir], stampMs), dir);
    } finally {
        await rm(dir, {recursive: true, force: true});
    }
});

test("findStaleInput: node_modules under an input root is ignored", async () => {
    const dir = await mkdtemp(join(tmpdir(), "ensure-storybook-static-"));
    try {
        const nested = join(dir, "node_modules", "storybook", "index.js");
        await mkdir(join(dir, "node_modules", "storybook"), {recursive: true});
        await writeFile(nested, "module.exports = {}\n");
        const newer = new Date(Date.now() + 60_000);
        const older = new Date(Date.now() - 60_000);
        await utimes(nested, newer, newer);
        await utimes(dir, older, older);
        assert.equal(findStaleInput([dir], Date.now()), null);
    } finally {
        await rm(dir, {recursive: true, force: true});
    }
});

test("decideRebuild: missing index.json reports the missing-output reason", () => {
    const reason = decideRebuild({
        ci: false,
        packageRoot: PACKAGE_ROOT,
        outputIndex: join(PACKAGE_ROOT, "storybook-static", "definitely-missing-index.json"),
    });
    assert.equal(reason, "missing storybook-static/index.json");
});

test("decideRebuild: a stamp newer than every input skips; a newer Menu.tsx does not", async () => {
    const workspace = await mkdtemp(join(tmpdir(), "ensure-storybook-ws-"));
    const packageRoot = join(workspace, "storybook");
    try {
        await mkdir(join(packageRoot, ".storybook"), {recursive: true});
        await mkdir(join(packageRoot, "storybook-static"), {recursive: true});
        await mkdir(join(workspace, "frontend-shared", "src", "shared", "ui", "menu"), {recursive: true});
        await writeFile(join(packageRoot, "package.json"), "{}\n");
        await writeFile(join(packageRoot, "postcss.config.mjs"), "export default {};\n");
        await writeFile(join(packageRoot, "tsconfig.json"), "{}\n");
        await writeFile(join(packageRoot, ".storybook", "main.ts"), "export default {};\n");
        await writeFile(join(workspace, "frontend-shared", "package.json"), "{}\n");
        const menu = join(workspace, "frontend-shared", "src", "shared", "ui", "menu", "Menu.tsx");
        await writeFile(menu, "export const Menu = {};\n");
        const indexPath = join(packageRoot, "storybook-static", "index.json");
        await writeFile(indexPath, "{}\n");

        const now = Date.now();
        const older = new Date(now - 60_000);
        const stamp = new Date(now);
        const newer = new Date(now + 60_000);
        for (const path of [
            join(packageRoot, "package.json"),
            join(packageRoot, "postcss.config.mjs"),
            join(packageRoot, "tsconfig.json"),
            join(packageRoot, ".storybook"),
            join(packageRoot, ".storybook", "main.ts"),
            join(workspace, "frontend-shared", "package.json"),
            join(workspace, "frontend-shared", "src"),
            join(workspace, "frontend-shared", "src", "shared"),
            join(workspace, "frontend-shared", "src", "shared", "ui"),
            join(workspace, "frontend-shared", "src", "shared", "ui", "menu"),
            menu,
        ]) {
            await utimes(path, older, older);
        }
        await utimes(indexPath, stamp, stamp);

        assert.equal(decideRebuild({ci: false, packageRoot, outputIndex: indexPath}), null);

        await utimes(menu, newer, newer);
        assert.match(decideRebuild({ci: false, packageRoot, outputIndex: indexPath}) ?? "", /Menu\.tsx/);
    } finally {
        await rm(workspace, {recursive: true, force: true});
    }
});
