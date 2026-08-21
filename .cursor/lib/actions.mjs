/**
 * Single source of truth for the frontend command harness: which script
 * names it governs, how each is actually invoked, and where the one wrapper
 * agents must go through lives. Imported by both
 * `.cursor/hooks/enforce-frontend-commands.mjs` (denies anything that
 * bypasses the wrapper) and `.cursor/cli/agent-check.mjs` (the wrapper
 * itself) so the two can never drift apart — see
 * `.cursor/rules/frontend-command-harness.mdc` for why this exists at all.
 */

export const WRAPPER_SCRIPT = ".cursor/cli/agent-check.mjs";

/**
 * scope:
 *  - "repo-only": only the root package.json defines this; pointing it at
 *    one workspace member would run a script that member doesn't have.
 *  - "repo-or-package": the root fans this out recursively, or it can be
 *    pointed at exactly one member.
 *  - "package-only": no root equivalent exists; a member is required.
 */
export const CONTROLLED_ACTIONS = [
  { name: "typecheck", scope: "repo-or-package" },
  { name: "lint", scope: "repo-only" },
  { name: "lint:fix", scope: "repo-only" },
  { name: "arch", scope: "repo-or-package" },
  { name: "arch:fsd", scope: "package-only" },
  { name: "arch:graph", scope: "package-only" },
  { name: "test", scope: "repo-or-package" },
  { name: "build", scope: "repo-or-package" },
  { name: "verify", scope: "repo-only" },
  { name: "tokens:generate", scope: "repo-or-package" },
  { name: "tokens:check", scope: "repo-or-package" },
  { name: "graph", scope: "package-only" },
  { name: "test:e2e", scope: "package-only" },
  { name: "test:visual", scope: "package-only" },
  { name: "test:visual:update", scope: "package-only" },
  { name: "test:a11y", scope: "package-only" },
  { name: "test:contrast", scope: "package-only" },
  { name: "test:contrast:wcag", scope: "package-only" },
  { name: "test:contrast:apca", scope: "package-only" },
  { name: "test:scoped", scope: "package-only" },
  { name: "test:mutation", scope: "package-only" },
  { name: "storybook", scope: "package-only" },
  { name: "build-storybook", scope: "package-only" },
];

export const CONTROLLED_ACTION_NAMES = CONTROLLED_ACTIONS.map((action) => action.name);

export function findAction(name) {
  return CONTROLLED_ACTIONS.find((action) => action.name === name);
}

export function isPathSelector(value) {
  return typeof value === "string" && (value.startsWith("./") || value.startsWith("../"));
}

export function escapeRegExp(literal) {
  return literal.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Longest-first: regex alternation matches the first alternative that fits
// at a given position, so without this "test" would win over "test:e2e" and
// every message referencing this alternation would name the truncated,
// wrong script.
export function controlledActionAlternation() {
  return [...CONTROLLED_ACTION_NAMES]
    .sort((a, b) => b.length - a.length)
    .map(escapeRegExp)
    .join("|");
}

/**
 * Builds the pnpm argv for a controlled action. `packagePath`, if given, is
 * normalized to a leading "./" so a caller passing "frontend-web" and one
 * passing "./frontend-web" produce the identical, canonical selector.
 */
export function buildPnpmArgs(actionName, packagePath) {
  const args = [];
  if (packagePath) {
    args.push("--filter", isPathSelector(packagePath) ? packagePath : `./${packagePath}`);
  }
  args.push("run", actionName);
  return args;
}
