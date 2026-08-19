#!/usr/bin/env node
/**
 * Reports what `/update-snapshots <module>` did, in the place the request was
 * made.
 *
 * All three outcomes get a comment, including "nothing changed" — a command
 * that silently does nothing is indistinguishable from one that silently
 * broke, and the difference matters to whoever is waiting.
 *
 * Environment: GITHUB_TOKEN, REPOSITORY, ISSUE_NUMBER, RUN_URL, JOB_STATUS,
 * BASELINES_CHANGED, MODULE.
 */
import { api, repository, required, run } from "./lib/actions.mjs";

await run(async () => {
    const { owner, repo } = repository();
    const issueNumber = required("ISSUE_NUMBER");
    const runUrl = required("RUN_URL");
    // Absent when the guard step itself failed (a missing/invalid module never
    // reaches this far) — falls back to a name that still reads sensibly.
    const module = process.env.MODULE || "the requested module";

    const failed = process.env.JOB_STATUS !== "success";
    const changed = process.env.BASELINES_CHANGED === "true";

    const body = failed
        ? `\`/update-snapshots\` failed for \`${module}\` — see the [run](${runUrl}).`
        : changed
            ? `Baselines updated for \`${module}\` and pushed to this branch. The visual check will re-run on its own.`
            : `No baseline changed for \`${module}\` — nothing to accept. See the [run](${runUrl}).`;

    await api(`/repos/${owner}/${repo}/issues/${issueNumber}/comments`, {
        method: "POST",
        body: { body },
    });
});
