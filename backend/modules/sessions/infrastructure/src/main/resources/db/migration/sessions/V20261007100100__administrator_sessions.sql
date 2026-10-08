-- The administrators' own kind of session (ADR-097): a session of the `admin` client lives only on the
-- administrators' site, and has lifetimes of its own.
--
-- The two checks are widened by one word. The code that runs while this release rolls out never writes
-- the new word, so it keeps working against the wider check; it does read the lifetime versions, one
-- row for every kind of client, and the row below is the one it does not know (ADR-066). It is added in
-- this release and not a later one because the code of this release refuses to run without it
-- (`LifetimeRules` demands lifetimes for every kind of client).
alter table sessions.sessions
    drop constraint sessions_client_type_check,
    add constraint sessions_client_type_check check (client_type in ('browser', 'admin'));

alter table sessions.lifetime_versions
    drop constraint lifetime_versions_client_type_check,
    add constraint lifetime_versions_client_type_check check (client_type in ('browser', 'admin'));

-- What an administrator starts with: an hour unused, eight hours at most, and a proof stays fresh for five
-- minutes, as version 1 and in force. Values, not bounds: the screen of slice 7 changes them.
insert into sessions.lifetime_versions (client_type, number, idle_millis, absolute_millis, freshness_millis, created_at)
values ('admin', 1, 3600000, 28800000, 300000, now());

insert into sessions.lifetime_activations (client_type, number, activated_at)
values ('admin', 1, now());
