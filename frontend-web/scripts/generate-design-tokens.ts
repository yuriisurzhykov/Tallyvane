/**
 * The one place the token compiler is ever imported from this app.
 *
 * It writes two artefacts. `generated/tokens.css` is a static stylesheet, not a
 * runtime-injected `<style>` tag, so the tokens are present in the very first
 * paint. `generated/resolved.ts` is the same data already resolved to plain
 * values, for the consumers that have no CSS engine to lean on — anything
 * drawing to a canvas, an image or a chart. Those consumers may import the
 * generated file and nothing else: reaching into the token source directly
 * would drag this compiler into the bundle.
 *
 * The theme SOURCE lives in the `frontend-shared` package, not in this app —
 * `frontend-app` and `frontend-admin` have identical copies of this script pointed at the same
 * `THEME_DIR`, so there is one generated artefact, not two that could
 * silently disagree (see `packages/frontend-shared/README.md`, "Token
 * generation stays two call sites, one destination"). `compilerInput` is
 * imported as a real package dependency (`frontend-shared`, `workspace:*`);
 * `THEME_DIR` is a monorepo-relative filesystem path because this script
 * writes files directly and runs under `tsx`, never through Next's bundler,
 * so the Turbopack tsconfig-`paths` trap this repo has hit before does not
 * apply here.
 *
 * Run through `pnpm tokens:generate`.
 */
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { compileDesignTokens, DesignTokenBuildError } from "design-token-engine";
import compilerInput from "frontend-shared/ui/theme/compiler.config";

// `import.meta.url` rather than `__dirname`: this package is `"type": "module"`,
// where the CommonJS globals simply do not exist.
const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const THEME_DIR = path.resolve(SCRIPT_DIR, "../../packages/frontend-shared/src/shared/ui/theme");
const GENERATED_DIR = path.join(THEME_DIR, "generated");
const ADAPTERS_DIR = path.join(THEME_DIR, "adapters");

const REFERENCED_VARIABLE = /var\(\s*(--ds-[a-z0-9-]+)/g;
const DECLARED_VARIABLE = /^\s*(--ds-[a-z0-9-]+)\s*:/gm;

/** Both regexes above have exactly one, always-matching capture group — `RegExpMatchArray`'s index signature still widens it to `string | undefined`, since TS cannot see that guarantee from the pattern itself. */
function requiredCapture(match: RegExpMatchArray, index: number): string {
    const value = match[index];
    if (value === undefined) throw new Error(`Expected capture group ${String(index)} to be present in "${match[0]}"`);
    return value;
}

/**
 * Fails the build if an adapter gives a primitive a class-facing name.
 *
 * The rule it enforces: nothing outside `shared/ui/theme` may know that
 * primitives exist. Every category, with no exceptions and no category-by-
 * category judgement — a rule with a list of exemptions attached is one that
 * has to be re-argued each time the list is touched, and the answer drifts.
 *
 * An adapter is the only place the boundary can be breached, because
 * registering `--color-x: var(--ds-color-neutral-700)` mints the class `bg-x`
 * and hands the whole project a route around the semantic layer. Once such a
 * class exists it gets used, and no later review finds every instance.
 *
 * It cannot be done by pattern: a colour role and a colour primitive both
 * compile to `--ds-color-*`. The compiler reports the exact primitive names, so
 * this is an exact-membership test rather than a guess about shapes.
 */
async function auditAdapters(
    css: string,
    primitiveVariables: Readonly<Record<string, readonly string[]>>,
): Promise<string[]> {
    const owner = new Map<string, string>();
    for (const [category, names] of Object.entries(primitiveVariables)) {
        for (const name of names) owner.set(name, category);
    }
    // Everything the compiler actually declared, taken from the generated
    // stylesheet rather than rebuilt here, so the two cannot drift.
    const declared = new Set([...css.matchAll(DECLARED_VARIABLE)].map((match) => requiredCapture(match, 1)));

    const violations: string[] = [];
    for (const file of await readdir(ADAPTERS_DIR)) {
        if (!file.endsWith(".css")) continue;
        const contents = await readFile(path.join(ADAPTERS_DIR, file), "utf8");
        for (const match of contents.matchAll(REFERENCED_VARIABLE)) {
            const name = requiredCapture(match, 1);
            const category = owner.get(name);
            if (category) {
                violations.push(`${file}: ${name} is a ${category} primitive — expose the semantic role instead`);
            } else if (!declared.has(name)) {
                // Renaming a role leaves the adapter pointing at a variable
                // that no longer exists. CSS does not complain: the `var()`
                // resolves to nothing, the declaration is dropped, and the
                // style is simply absent wherever that class is used.
                violations.push(`${file}: ${name} is not declared by the compiler — renamed or misspelled`);
            }
        }
    }
    return [...new Set(violations)].sort();
}

const BANNER = [
    "/*",
    " * AUTO-GENERATED FILE. DO NOT EDIT MANUALLY.",
    " * Source: packages/frontend-shared/src/shared/ui/theme/{tokens,contracts,themes,composites}/",
    " * Generator: frontend-web/scripts/generate-design-tokens.ts (frontend-app and frontend-admin have identical copies)",
    " *",
    " * Already-resolved token data, holding no `{reference}` strings and needing",
    " * no compiler to read. This is the only token artefact a non-CSS consumer",
    " * may import.",
    " */",
].join("\n");

function serializeResolvedModule(resolved: unknown): string {
    return `${BANNER}\nexport const resolved = ${JSON.stringify(resolved, null, 4)} as const;\n`;
}

/**
 * True when the file on disk already holds exactly this content. Missing counts
 * as different rather than as an error, which is the honest answer for a check
 * run before anything has ever been generated.
 */
async function isUnchanged(filePath: string, content: string): Promise<boolean> {
    try {
        return (await readFile(filePath, "utf8")) === content;
    } catch {
        return false;
    }
}

async function main(): Promise<void> {
    // `--check` verifies instead of writing: the generated files are committed,
    // so CI has to be able to prove they match their source. Without it, a
    // change to a token that nobody regenerated would sail through review and
    // then diverge silently from the CSS the app actually loads.
    const checkOnly = process.argv.includes("--check");
    const { css, resolved, warnings, primitiveVariables } = compileDesignTokens(compilerInput);

    for (const warning of warnings) {
        console.warn(`[tokens] ${warning}`);
    }

    // Runs on every generate, not only under `--check`: the point is that
    // neither fault can survive long enough to reach a commit.
    const violations = await auditAdapters(css, primitiveVariables);
    if (violations.length > 0) {
        console.error(`\n[tokens] Adapter problems:\n${violations.map((v) => `  - ${v}`).join("\n")}\n`);
        process.exitCode = 1;
        return;
    }

    const artefacts = [
        { path: path.join(GENERATED_DIR, "tokens.css"), content: `${css}\n` },
        { path: path.join(GENERATED_DIR, "resolved.ts"), content: serializeResolvedModule(resolved) },
    ];

    if (checkOnly) {
        const stale: string[] = [];
        for (const artefact of artefacts) {
            if (!(await isUnchanged(artefact.path, artefact.content))) stale.push(artefact.path);
        }
        if (stale.length > 0) {
            console.error(
                `\n[tokens] Out of date with the token source:\n${stale.map((p) => `  - ${p}`).join("\n")}\n\n` +
                "Run `pnpm tokens:generate` and commit the result.\n",
            );
            process.exitCode = 1;
            return;
        }
        console.log("[tokens] Generated files match the source.");
        return;
    }

    await mkdir(GENERATED_DIR, { recursive: true });
    for (const artefact of artefacts) {
        await writeFile(artefact.path, artefact.content, "utf8");
        console.log(`Generated ${artefact.path}`);
    }
}

main().catch((error: unknown) => {
    // A build error carries an already-formatted, human-readable rule violation;
    // anything else is a genuine crash and deserves its stack.
    if (error instanceof DesignTokenBuildError) {
        console.error(`\n[tokens:generate] Build failed:\n\n${error.message}\n`);
    } else {
        console.error(error);
    }
    process.exitCode = 1;
});
