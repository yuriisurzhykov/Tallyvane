-- Who made each version of a policy and who put each in force, and why (ADR-098).
--
-- Additive, so the previous release keeps working while this one rolls out (ADR-066): the columns are nullable and
-- the old code never reads them. The rows the earlier migrations made, the first version of each purpose and its
-- activation, were made by nobody, and say so with a null; a row made through the API has both its author and its
-- comment. The two columns of a row are both there or both missing, so a screen never shows an author without
-- words or words without an author.
--
-- The author is an account identifier and not a reference: accounts belong to another schema (ADR-044).

alter table authentication.policy_versions
    add column made_by uuid null,
    add column comment text null,
    add constraint policy_versions_author_and_comment_go_together check ((made_by is null) = (comment is null)),
    add constraint policy_versions_comment_length check (comment is null or char_length(comment) between 1 and 500);

alter table authentication.policy_activations
    add column activated_by uuid null,
    add column reason text null,
    add constraint policy_activations_author_and_reason_go_together check ((activated_by is null) = (reason is null)),
    add constraint policy_activations_reason_length check (reason is null or char_length(reason) between 1 and 500);
