-- The administrators' own kind of session (ADR-097): a session of the `admin` client lives only on the
-- administrators' site, and has lifetimes of its own.
--
-- The two checks are widened by one word and nothing else changes. The code that runs while this release
-- rolls out never writes the new word, so it keeps working against the wider check. No lifetime version is
-- added for `admin` here: the previous release reads every lifetime row it finds and does not know that word
-- (ADR-066), so a row added now would stop it serving until it was retired. Until a version is kept, an
-- administrator's session is judged by the starting lifetimes the code carries (`ClientType.Admin`: an hour
-- idle, eight hours at most, five minutes of freshness). The row, and the screen that edits it, arrive with
-- the versions API of slice 7b.
alter table sessions.sessions
    drop constraint sessions_client_type_check,
    add constraint sessions_client_type_check check (client_type in ('browser', 'admin'));

alter table sessions.lifetime_versions
    drop constraint lifetime_versions_client_type_check,
    add constraint lifetime_versions_client_type_check check (client_type in ('browser', 'admin'));
