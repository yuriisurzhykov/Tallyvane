#!/usr/bin/env node
// Run directly: node .cursor/cli/agent-check.test.mjs
import assert from "node:assert/strict";
import test from "node:test";
import {parseArgs, validate} from "./agent-check.mjs";

test("parseArgs: action alone", () => {
    assert.deepEqual(parseArgs(["typecheck"]), {action: "typecheck", packagePath: undefined, passthrough: []});
});

test("parseArgs: --package <path> form", () => {
    assert.deepEqual(parseArgs(["test:e2e", "--package", "frontend-web"]), {
        action: "test:e2e",
        packagePath: "frontend-web",
        passthrough: [],
    });
});

test("parseArgs: --package=<path> form", () => {
    assert.deepEqual(parseArgs(["test:e2e", "--package=frontend-web"]), {
        action: "test:e2e",
        packagePath: "frontend-web",
        passthrough: [],
    });
});

test("parseArgs: passthrough args after --", () => {
    assert.deepEqual(parseArgs(["test:scoped", "--package", "packages/storybook", "--", "visual.spec.ts", "-g", "Button"]), {
        action: "test:scoped",
        packagePath: "packages/storybook",
        passthrough: ["visual.spec.ts", "-g", "Button"],
    });
});

test("parseArgs: --help short-circuits everything", () => {
    assert.deepEqual(parseArgs(["--help"]), {help: true});
    assert.deepEqual(parseArgs([]), {help: true});
});

test("validate: unknown action is an error, not a throw", () => {
    const {action, error} = validate({action: "not-a-real-action"});
    assert.equal(action, undefined);
    assert.match(error, /unknown action/);
});

test("validate: repo-only action rejects --package", () => {
    const {error} = validate({action: "lint", packagePath: "frontend-web"});
    assert.match(error, /no per-package form/);
});

test("validate: package-only action requires --package", () => {
    const {error} = validate({action: "graph"});
    assert.match(error, /requires --package/);
});

test("validate: repo-or-package action accepts either", () => {
    assert.equal(validate({action: "typecheck"}).action.name, "typecheck");
    assert.equal(validate({action: "typecheck", packagePath: "frontend-web"}).action.name, "typecheck");
});
