-- Who made each version of the session lifetimes and who put each in force, and why (ADR-098); and the
-- administrators' lifetimes as version 1, in force, so that the screen that edits them has a version to start from.
--
-- Additive, so the previous release keeps working while this one rolls out (ADR-066): the columns are nullable and
-- the old code never reads them. The rows the earlier migrations made, the first version of the browser's lifetimes
-- and its activation, were made by nobody, and say so with a null; a row made through the API has both its author
-- and its comment. The two columns of a row are both there or both missing, so a screen never shows an author
-- without words or words without an author.
--
-- The author is an account identifier and not a reference: accounts belong to another schema (ADR-044).

alter table sessions.lifetime_versions
    add column made_by uuid null,
    add column comment text null,
    add constraint lifetime_versions_author_and_comment_go_together check ((made_by is null) = (comment is null)),
    add constraint lifetime_versions_comment_length check (comment is null or char_length(comment) between 1 and 500);

alter table sessions.lifetime_activations
    add column activated_by uuid null,
    add column reason text null,
    add constraint lifetime_activations_author_and_reason_go_together check ((activated_by is null) = (reason is null)),
    add constraint lifetime_activations_reason_length check (reason is null or char_length(reason) between 1 and 500);

-- The administrators' lifetimes, which the previous migration left to the code (`ClientType.Admin`): an hour unused,
-- eight hours at most, five minutes of freshness. The release that wrote that migration reads this row as it reads the
-- browser's, because it already knows the word `admin`. The code keeps the same numbers as the starting lifetimes of a
-- database that has no row, which is no longer a database this release meets.
insert into sessions.lifetime_versions (client_type, number, idle_millis, absolute_millis, freshness_millis, created_at)
values ('admin', 1, 3600000, 28800000, 300000, now());

insert into sessions.lifetime_activations (client_type, number, activated_at)
values ('admin', 1, now());
