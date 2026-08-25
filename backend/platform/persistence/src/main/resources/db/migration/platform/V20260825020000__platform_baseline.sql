-- The one extension every capability relies on.
--
-- citext is text that compares without regard to case, which is what an email
-- address needs: a person typing a different case is the same person. It is a
-- trusted extension since PostgreSQL 13, so the application's own role may install
-- it given CREATE on the database — no superuser step on the server.
--
-- The `platform` schema itself is not created here: Flyway has to create it before
-- this file can run, because its own history table lives there.
create extension if not exists citext schema platform;

-- Without this, citext silently stops being citext.
--
-- An extension's comparison operators live in the schema it was installed into. If
-- that schema is not on search_path when a query runs, PostgreSQL does not find them
-- and falls back to ordinary case-sensitive text comparison - no error, no warning,
-- just an address that no longer matches itself. Setting it on the database rather
-- than per connection covers every session: the application, psql, and Flyway's own
-- later runs.
--
-- current_database() through a DO block because ALTER DATABASE takes a name, not an
-- expression.
do $$
begin
    execute format('alter database %I set search_path to public, platform', current_database());
end;
$$;
