-- Case-insensitive comparison, as a property of a column rather than of a session.
--
-- An email address must match itself typed in a different case: a person entering
-- Ivan@Mail.com and ivan@mail.com is one person, and two rows differing only by case
-- must be impossible.
--
-- This is an ICU collation rather than the citext extension, and the reason is that
-- citext cannot give the guarantee. An extension's comparison operators live in the
-- schema it was installed into, and if that schema is not on search_path when a query
-- runs, PostgreSQL silently falls back to case-sensitive text comparison - no error,
-- no warning, just a person unable to sign in. search_path is session state, and
-- setting it on the database does not survive `create database ... template ...`,
-- which is how every integration test gets its database. Measured, not assumed.
--
-- A collation is bound to the column when the table is created and stored with it. It
-- depends on nothing at query time and travels with the schema when a database is
-- cloned, so it cannot silently stop working.
--
-- Columns declare it qualified: `email text collate platform.case_insensitive`.
--
-- One limitation to know rather than discover: PostgreSQL refuses LIKE on a
-- non-deterministic collation. Case-insensitive prefix search is `lower(email) like
-- lower(...)`, over an expression index where it matters.
create collation platform.case_insensitive (
    provider = icu,
    locale = 'und-u-ks-level2',
    deterministic = false
);
