/**
 * Writes `src/shared/api/generated/schema.d.ts` from `docs/openapi.yaml`, or with `--check` compares
 * what it would write with what is committed and fails if they differ. The same pair of commands the
 * design tokens have (`tokens:generate` / `tokens:check`), and for the same reason (ADR-064): the
 * types are committed, so a change to the specification that breaks a client shows up as a diff in
 * review, and `--check` in `arch` makes forgetting to regenerate a red build.
 */
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import openapiTS, { astToString } from "openapi-typescript";

const SPEC = new URL("../../../docs/openapi.yaml", import.meta.url);
const TARGET = new URL("../src/shared/api/generated/schema.d.ts", import.meta.url);
const HEADER = "/**\n * Generated from docs/openapi.yaml by scripts/generate-api-types.mjs. Do not edit: run `pnpm run api:generate`.\n */\n\n";

const generated = HEADER + astToString(await openapiTS(SPEC));

if (process.argv.includes("--check")) {
    const committed = await readFile(TARGET, "utf8").catch(() => "");
    if (committed !== generated) {
        console.error(`${fileURLToPath(TARGET)} is out of date with docs/openapi.yaml. Run: pnpm run api:generate`);
        process.exit(1);
    }
    console.log("API types are up to date.");
} else {
    await writeFile(TARGET, generated);
    console.log(`Wrote ${fileURLToPath(TARGET)}`);
}
