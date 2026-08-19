#!/usr/bin/env node
/**
 * Decides whether a `/update-snapshots` request may proceed, and resolves the
 * branch it refers to into something that cannot change underneath us.
 *
 * The workflow has already checked WHO asked. That says nothing about whose
 * code is about to run, which is the actual risk: a stranger opens a pull
 * request, a maintainer types the command, and the fork's own install scripts
 * and test configuration execute in a job holding write access to this
 * repository. So a branch outside this repository is refused outright.
 *
 * The commit SHA is the second half. Everything downstream checks out that
 * value rather than the branch name, because a branch can be repointed between
 * the moment it is verified and the moment it is used.
 *
 * Environment: GITHUB_TOKEN, REPOSITORY, ISSUE_NUMBER, COMMENT_ID.
 */
import { api, repository, required, run, setOutput } from "./lib/actions.mjs";

await run(async () => {
    const { owner, repo } = repository();
    const issueNumber = required("ISSUE_NUMBER");
    const commentId = required("COMMENT_ID");

    // Acknowledged first, and before anything that can fail: regenerating
    // baselines takes minutes, and an unacknowledged command looks ignored and
    // gets typed again.
    await api(`/repos/${owner}/${repo}/issues/comments/${commentId}/reactions`, {
        method: "POST",
        body: { content: "+1" },
    });

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

    setOutput("head_sha", pullRequest.head.sha);
    setOutput("head_ref", pullRequest.head.ref);
});
