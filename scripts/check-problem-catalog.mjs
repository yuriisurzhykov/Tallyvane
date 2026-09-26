import { readFile } from "node:fs/promises";

const [spec, rfcAnswers, statuses, actionSource, secondFactorProblems] = await Promise.all([
    readFile(new URL("../docs/openapi.yaml", import.meta.url), "utf8"),
    readFile(new URL("../backend/platform/http/src/main/kotlin/tallyvane/platform/http/status/Rfc9457Answers.kt", import.meta.url), "utf8"),
    readFile(new URL("../backend/platform/http/src/main/kotlin/tallyvane/platform/http/status/Statuses.kt", import.meta.url), "utf8"),
    readFile(new URL("../backend/modules/identity/domain/src/main/kotlin/tallyvane/identity/domain/secondfactor/AuthenticationAction.kt", import.meta.url), "utf8"),
    readFile(new URL("../backend/modules/identity/web/src/main/kotlin/tallyvane/identity/web/mfa/SecondFactorProblems.kt", import.meta.url), "utf8"),
]);

function fail(message) {
    process.stderr.write(`${message}\n`);
    process.exitCode = 1;
}

function component(name) {
    const lines = spec.split(/\r?\n/u);
    const start = lines.findIndex((line) => line === `    ${name}:`);
    if (start === -1) throw new Error(`OpenAPI component schema ${name} is missing`);

    let end = start + 1;
    while (end < lines.length && !/^    [A-Za-z][A-Za-z0-9]*:\s*$/u.test(lines[end])) end++;
    return lines.slice(start, end).join("\n");
}

function enumValues(block, label) {
    const lines = block.split("\n");
    const enumIndex = lines.findIndex((line) => /^\s*enum:\s*/u.test(line));
    if (enumIndex === -1) throw new Error(`${label} has no enum`);

    const enumLine = lines[enumIndex];
    const inline = enumLine.match(/^\s*enum:\s*\[(.*)\]\s*$/u);
    if (inline) {
        return inline[1].split(",").map((value) => value.trim().replace(/^['"]|['"]$/gu, ""));
    }

    const indent = enumLine.length - enumLine.trimStart().length;
    const values = [];
    for (let index = enumIndex + 1; index < lines.length; index++) {
        const line = lines[index];
        if (line.trim() === "") continue;
        const currentIndent = line.length - line.trimStart().length;
        if (currentIndent <= indent) break;
        if (currentIndent === indent + 2 && line.trimStart().startsWith("- ")) {
            values.push(line.trimStart().slice(2).trim().replace(/^['"]|['"]$/gu, ""));
        }
    }
    return values;
}

function propertyBlock(block, property, label) {
    const lines = block.split("\n");
    const start = lines.findIndex((line) => line === `        ${property}:`);
    if (start === -1) throw new Error(`${label}.${property} is missing`);

    let end = start + 1;
    while (end < lines.length) {
        const line = lines[end];
        if (line.trim() !== "" && (line.length - line.trimStart().length) <= 8) break;
        end++;
    }
    return lines.slice(start, end).join("\n");
}

function sortedUnique(values, label) {
    const duplicates = values.filter((value, index) => values.indexOf(value) !== index);
    if (duplicates.length > 0) throw new Error(`${label} contains duplicates: ${[...new Set(duplicates)].join(", ")}`);
    return [...values].sort();
}

function compare(label, actual, expected) {
    const actualSorted = sortedUnique(actual, label);
    const expectedSorted = sortedUnique(expected, `${label} source`);
    const missing = expectedSorted.filter((value) => !actualSorted.includes(value));
    const unexpected = actualSorted.filter((value) => !expectedSorted.includes(value));
    if (missing.length > 0 || unexpected.length > 0) {
        fail(`${label} drifted. Missing: [${missing.join(", ")}]. Unexpected: [${unexpected.join(", ")}].`);
    }
}

const uriPrefix = rfcAnswers.match(/const val PREFIX = "([^"]+)"/u)?.[1];
if (!uriPrefix) throw new Error("Rfc9457Answers.kt has no fixed RFC 9457 type URI prefix");

const backendTypes = [...rfcAnswers.matchAll(/\buri\("([a-z0-9-]+)"\)/gu)].map((match) => `${uriPrefix}${match[1]}`);
const aboutBlank = statuses.match(/const val BLANK = "([^"]+)"/u)?.[1];
if (!aboutBlank) throw new Error("Statuses.kt has no about:blank type");
backendTypes.push(aboutBlank);

const problemCatalog = enumValues(component("ProblemType"), "ProblemType");
compare("OpenAPI ProblemType vs Kotlin problem types", problemCatalog, backendTypes);

const stepUpType = `${uriPrefix}step-up-required`;
const ordinaryProblemTypes = problemCatalog.filter((type) => type !== stepUpType);
const ordinaryTypeSchema = propertyBlock(component("ProblemWithoutAction"), "type", "ProblemWithoutAction");
compare(
    "ProblemWithoutAction type catalog",
    enumValues(ordinaryTypeSchema, "ProblemWithoutAction.type"),
    ordinaryProblemTypes,
);

const stepUpSchema = component("StepUpRequiredProblem");
const stepUpTypeProperty = propertyBlock(stepUpSchema, "type", "StepUpRequiredProblem");
if (!stepUpTypeProperty.includes(`const: ${stepUpType}`)) {
    fail(`StepUpRequiredProblem.type must be ${stepUpType}`);
}
if (!/^\s{10}const:\s*403\s*$/mu.test(propertyBlock(stepUpSchema, "status", "StepUpRequiredProblem"))) {
    fail("StepUpRequiredProblem.status must be the fixed HTTP 403 code");
}
if (!propertyBlock(stepUpSchema, "action", "StepUpRequiredProblem").includes('"#/components/schemas/AuthenticationAction"')) {
    fail("StepUpRequiredProblem.action must reference the shared AuthenticationAction schema");
}
if (!/^\s{6}not:\s*\r?\n^\s{8}required:\s*\[action\]\s*$/mu.test(component("ProblemWithoutAction"))) {
    fail("ProblemWithoutAction must reject the step-up-only action extension");
}

const openApiActions = enumValues(component("AuthenticationAction"), "AuthenticationAction");
const actionBody = actionSource.match(/enum class AuthenticationAction\s*\{([\s\S]*?)\}/u)?.[1];
if (!actionBody) throw new Error("AuthenticationAction.kt enum is missing");
const backendActions = [...actionBody.replace(/\/\/.*$/gmu, "").matchAll(/^\s*([A-Z][A-Z0-9_]*)\s*,?\s*$/gmu)]
    .map((match) => match[1]);
compare("OpenAPI AuthenticationAction vs Kotlin enum", openApiActions, backendActions);
if (!/ReauthenticationRequired\s*->\s*stepUpRequired\(AuthenticationAction\.MANAGE_SECOND_FACTORS\)/u.test(secondFactorProblems)) {
    fail("MFA reauthentication failures must emit the typed MANAGE_SECOND_FACTORS step-up problem");
}

if (process.exitCode !== 1) process.stdout.write("Problem catalog and action enum match their Kotlin sources.\n");
