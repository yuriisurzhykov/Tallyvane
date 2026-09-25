import { readFile } from "node:fs/promises";

const spec = await readFile(new URL("../docs/openapi.yaml", import.meta.url), "utf8");
const lines = spec.split(/\r?\n/u);
const violations = [];
let propertiesIndent = null;

for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    const indent = line.length - line.trimStart().length;

    if (/^\s*properties:\s*$/u.test(line)) {
        propertiesIndent = indent;
        continue;
    }
    if (propertiesIndent === null || line.trim() === "") continue;
    if (indent <= propertiesIndent) {
        propertiesIndent = null;
        continue;
    }
    if (indent !== propertiesIndent + 2) continue;

    const property = line.match(/^\s*([A-Za-z][A-Za-z0-9]*):/u)?.[1];
    if (property && !/^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$/u.test(property)) {
        violations.push(`${index + 1}: ${property}`);
    }
}

if (violations.length > 0) {
    process.stderr.write(`OpenAPI JSON properties must use snake_case:\n${violations.join("\n")}\n`);
    process.exitCode = 1;
}
