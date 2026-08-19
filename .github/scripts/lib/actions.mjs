import fs from "node:fs";

/**
 * The smallest useful GitHub API client, and the reason it is hand-written
 * rather than a dependency.
 *
 * A workflow step that talks to the API usually reaches for
 * `actions/github-script`, which means the logic ends up as a JavaScript string
 * inside a YAML file: no syntax highlighting, no linting, no tests, and a diff
 * nobody can read. Moving it into real files means it is checked by the same
 * tools as everything else — and it also removes a third-party action from the
 * one job that holds a token with write access.
 *
 * Node has had `fetch` built in for years, so the whole client is a couple of
 * dozen lines and pulls in nothing.
 */

const API = "https://api.github.com";

export async function api(path, { method = "GET", body } = {}) {
    const token = process.env.GITHUB_TOKEN;
    if (!token) throw new Error("GITHUB_TOKEN is not set — the workflow step must pass it through `env`.");

    const response = await fetch(`${API}${path}`, {
        method,
        headers: {
            authorization: `Bearer ${token}`,
            accept: "application/vnd.github+json",
            // Pinned rather than left to the default, so a future default
            // change alters nothing here without someone deciding to.
            "x-github-api-version": "2022-11-28",
            ...(body ? { "content-type": "application/json" } : {}),
        },
        ...(body ? { body: JSON.stringify(body) } : {}),
    });

    if (!response.ok) {
        throw new Error(`${method} ${path} responded ${response.status}: ${await response.text()}`);
    }
    return response.status === 204 ? null : response.json();
}

/** Reads an environment variable a script cannot run without, rather than proceeding with `undefined` and failing later somewhere confusing. */
export function required(name) {
    const value = process.env[name];
    if (!value) throw new Error(`${name} is not set — the workflow step must pass it through \`env\`.`);
    return value;
}

/** `owner` and `repo` from the `owner/repo` string every workflow already has. */
export function repository() {
    const [owner, repo] = required("REPOSITORY").split("/");
    return { owner, repo };
}

export function setOutput(name, value) {
    fs.appendFileSync(required("GITHUB_OUTPUT"), `${name}=${value}\n`);
}

/** The `::error::` prefix is what puts the message on the job's summary page instead of only in the log. */
export function fail(message) {
    console.error(`::error::${message}`);
    process.exitCode = 1;
}

/**
 * Runs a script's body and turns any thrown error into a failed step with a
 * readable message. Without this a rejected promise prints a stack trace and,
 * depending on the Node version, may not even set a non-zero exit code — a step
 * that failed and reported success.
 */
export async function run(main) {
    try {
        await main();
    } catch (error) {
        fail(error instanceof Error ? error.message : String(error));
    }
}
