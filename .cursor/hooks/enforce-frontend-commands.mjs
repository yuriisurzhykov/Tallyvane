#!/usr/bin/env node
/**
 * beforeShellExecution hook: the one wrapper script,
 * `.cursor/cli/agent-check.mjs` (see `.cursor/rules/frontend-command-harness.mdc`),
 * is the only sanctioned way to run a controlled build/test/lint/check
 * action. This hook denies any command that invokes one of those actions
 * directly — through pnpm/npm/yarn, correctly formed or not, or through a
 * bare direct-binary bypass (`npx eslint` standing in for `pnpm run lint`) —
 * and allows everything else, including the wrapper itself and any
 * pnpm/npm/yarn/npx command unrelated to a controlled action.
 *
 * Exported functions are plain and synchronous specifically so
 * enforce-frontend-commands.test.mjs can exercise the decision logic
 * directly, without going through stdin/stdout at all.
 */
import {pathToFileURL} from "node:url";
import {CONTROLLED_ACTION_NAMES, WRAPPER_SCRIPT} from "../lib/actions.mjs";

const PACKAGE_MANAGERS = new Set(["pnpm", "npm", "yarn"]);

// pnpm/yarn allow omitting "run" for any script; npm allows it only for a
// handful of special-cased names, "test" among them, which is one of ours —
// so "run" has to be treated as always-optional here, not npm-specific.
const RUN_KEYWORD = "run";

// Flags pnpm/npm/yarn take a separate value for, so the scan below doesn't
// mistake the value (e.g. the path after --filter) for the script name.
const FLAGS_WITH_VALUE = new Set(["--filter", "-C", "--dir"]);

const RULE_DOC = ".cursor/rules/frontend-command-harness.mdc";

// Narrow on purpose: only fires when the whole segment is the bare tool
// invocation (plus flags), never when it carries a file/path argument —
// `npx eslint src/foo.ts` is a normal one-file debug command, not a bypass
// of the real check.
const DIRECT_BYPASSES = [
    {re: /^(?:npx\s+|node_modules[\\/]\.bin[\\/])eslint(?:\s+--\S+)*\s*$/, action: "lint"},
    {re: /^(?:npx\s+|node_modules[\\/]\.bin[\\/])tsc(?:\s+--\S+)*\s*$/, action: "typecheck"},
    {
        re: /^(?:npx\s+|node_modules[\\/]\.bin[\\/])playwright\s+test(?:\s+--\S+)*\s*$/,
        action: "test:e2e",
        packageHint: true,
    },
    {re: /^(?:npx\s+|node_modules[\\/]\.bin[\\/])vitest(?:\s+run)?(?:\s+--\S+)*\s*$/, action: "test"},
];

function deny(reason) {
    return {
        permission: "deny",
        agent_message: `frontend-command-harness: ${reason} See ${RULE_DOC}.`,
        user_message: `Blocked by the frontend command harness: ${reason}`,
    };
}

function allow() {
    return {permission: "allow"};
}

function splitSegments(command) {
    return command
        .split(/&&|\|\||;|\|/)
        .map((segment) => segment.trim())
        .filter(Boolean);
}

/** Whitespace-splits a segment, keeping quoted strings as one token. */
function tokenize(segment) {
    const tokens = [];
    const pattern = /"([^"]*)"|'([^']*)'|(\S+)/g;
    let match;
    while ((match = pattern.exec(segment)) !== null) {
        tokens.push(match[1] ?? match[2] ?? match[3]);
    }
    return tokens;
}

function packageManagerName(token) {
    const bare = token.replace(/\.(cmd|exe|ps1)$/i, "");
    if (PACKAGE_MANAGERS.has(token)) return token;
    if (PACKAGE_MANAGERS.has(bare)) return bare;
    return null;
}

/**
 * Scans tokens for `<manager> [flags] [run] <script>` and returns the
 * manager + script name the moment it finds a controlled one — comparing
 * whole tokens, never a substring/regex match, specifically so `pnpm why
 * lint-staged` can never be confused with running the `lint` script.
 */
function findControlledInvocation(tokens) {
    for (let i = 0; i < tokens.length; i += 1) {
        const manager = packageManagerName(tokens[i]);
        if (!manager) continue;

        let j = i + 1;
        while (j < tokens.length) {
            const token = tokens[j];
            if (token === RUN_KEYWORD) {
                j += 1;
                continue;
            }
            if (FLAGS_WITH_VALUE.has(token)) {
                j += 2;
                continue;
            }
            if (token.startsWith("-")) {
                j += 1;
                continue;
            }
            // First real positional argument: the script name if this invocation
            // runs one, or something else (install/add/why/a package name/...)
            // that means this isn't a controlled-action invocation at all.
            if (CONTROLLED_ACTION_NAMES.includes(token)) {
                return {manager, script: token};
            }
            break;
        }
    }
    return null;
}

function isWrapperInvocation(segment) {
    return segment.includes("agent-check.mjs");
}

function evaluateSegment(segment) {
    if (isWrapperInvocation(segment)) return null;

    const invocation = findControlledInvocation(tokenize(segment));
    if (invocation) {
        return deny(
            `"${segment}" runs "${invocation.script}" directly via ${invocation.manager} instead of through the one wrapper. Use: node ${WRAPPER_SCRIPT} ${invocation.script} [--package <path>]`,
        );
    }

    for (const bypass of DIRECT_BYPASSES) {
        if (bypass.re.test(segment)) {
            const packageHint = bypass.packageHint ? " --package <path-to-package>" : "";
            return deny(
                `"${segment}" calls the tool directly, bypassing the wrapper. Use: node ${WRAPPER_SCRIPT} ${bypass.action}${packageHint}`,
            );
        }
    }

    return null;
}

/**
 * Decides whether `command` violates the harness. Pure and synchronous.
 */
export function evaluateCommand(command) {
    if (typeof command !== "string" || command.trim() === "") return allow();

    for (const segment of splitSegments(command)) {
        const denial = evaluateSegment(segment);
        if (denial) return denial;
    }

    return allow();
}

function readStdin() {
    return new Promise((resolve) => {
        let data = "";
        process.stdin.setEncoding("utf8");
        process.stdin.on("data", (chunk) => {
            data += chunk;
        });
        process.stdin.on("end", () => resolve(data));
    });
}

async function main() {
    const raw = await readStdin();
    let input;
    try {
        input = JSON.parse(raw);
    } catch {
        // Malformed input from the hook runner is not this hook's job to fail on —
        // allow, and let whatever actually runs the command surface the real error.
        process.stdout.write(JSON.stringify(allow()));
        return;
    }
    process.stdout.write(JSON.stringify(evaluateCommand(input.command)));
}

const isMainModule = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
    main();
}
