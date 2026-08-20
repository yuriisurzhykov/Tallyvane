#!/usr/bin/env node
/**
 * Decides whether a `/update-snapshots <module>` request may proceed, resolves
 * which workspace member it refers to, and resolves the branch into something
 * that cannot change underneath us.
 *
 * The workflow has already checked WHO asked. That says nothing about whose
 * code is about to run, which is the actual risk: a stranger opens a pull
 * request, a maintainer types the command, and the fork's own install scripts
 * and test configuration execute in a job holding write access to this
 * repository. So a branch outside this repository is refused outright.
 *
 * `<module>` is required, not defaulted to any one app — this workflow does
 * not know, and should not need to know, which workspace members have a
 * visual suite; the next job finds that out by trying `pnpm run
 * test:visual:update` in it, and fails loudly (a missing script, or a missing
 * directory) if the answer is "none". This script only checks that the text
 * is safe to use as a path segment, not that it names something real — this
 * job's checkout is deliberately sparse (`.github/scripts` only, see below)
 * and cannot see the rest of the repository to check further.
 *
 * The commit SHA is the other half. Everything downstream checks out that
 * value rather than the branch name, because a branch can be repointed between
 * the moment it is verified and the moment it is used.
 *
 * Environment: GITHUB_TOKEN, REPOSITORY, ISSUE_NUMBER, COMMENT_ID, COMMENT_BODY.
 */
import { api, repository, required, run, setOutput } from "./lib/actions.mjs";

// One optional `packages/` segment, then a lowercase-and-hyphens name — covers
// every real workspace member (`frontend-web`, `frontend-admin`,
// `packages/frontend-shared`, `packages/content-kit`, ...) and rejects the
// characters that would matter if this string ever reached a shell: `.`, `/`
// repeated, spaces, `$`, backticks, quotes.
const MODULE_PATTERN = /^(?:packages\/)?[a-z][a-z0-9-]*$/;

async function commentUsage(owner, repo, issueNumber, reason) {
    await api(`/repos/${owner}/${repo}/issues/${issueNumber}/comments`, {
        method: "POST",
        body: {
            body: `\`/update-snapshots\` was ignored — ${reason}\n\n`
                + "Usage: `/update-snapshots <module>`, where `<module>` is the workspace member "
                + "whose baselines should be regenerated, e.g. `frontend-web` or `packages/storybook`.",
        },
    });
}

await run(async () => {
    const { owner, repo } = repository();
    const issueNumber = required("ISSUE_NUMBER");
    const commentId = required("COMMENT_ID");
    const commentBody = required("COMMENT_BODY");

    // Acknowledged first, and before anything that can fail: regenerating
    // baselines takes minutes, and an unacknowledged command looks ignored and
    // gets typed again.
    await api(`/repos/${owner}/${repo}/issues/comments/${commentId}/reactions`, {
        method: "POST",
        body: { content: "+1" },
    });

    const match = commentBody.match(/\/update-snapshots\s+(\S+)/);
    if (!match) {
        await commentUsage(owner, repo, issueNumber, "no module was given.");
        throw new Error("No module argument in the /update-snapshots command.");
    }

    const module = match[1];
    if (!MODULE_PATTERN.test(module)) {
        await commentUsage(owner, repo, issueNumber, `\`${module}\` is not a valid module name.`);
        throw new Error(`Rejected module argument: ${module}`);
    }

    const pullRequest = await api(`/repos/${owner}/${repo}/pulls/${issueNumber}`);
    const isSameRepo = pullRequest.head.repo?.full_name === `${owner}/${repo}`;
    setOutput("is_fork", String(!isSameRepo));

    if (!isSameRepo) {
        await api(`/repos/${owner}/${repo}/issues/${issueNumber}/comments`, {
            method: "POST",
            body: {
                body: "`/update-snapshots` was ignored — this pull request's branch is not in this "
                    + "repository. Running it would mean checking out and executing the fork's own "
                    + "code while the job holds write access here.",
            },
        });
        throw new Error("Refusing to run against a fork branch.");
    }

    setOutput("module", module);
    setOutput("head_sha", pullRequest.head.sha);
    setOutput("head_ref", pullRequest.head.ref);
});
