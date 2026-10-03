-- When a person last proved who they are, and how long such a proof stays fresh (ADR-092).
--
-- A session now says when its person last proved who they are: at first the moment it began, and later
-- the moment they confirmed a dangerous act. That is a separate column from `authenticated_at` because
-- confirming must not make a session live longer, and the absolute lifetime counts from the sign-in.
-- The rows that exist were all begun by a sign-in, so their last proof is that.

alter table sessions.sessions
    add column confirmed_at timestamptz;

update sessions.sessions
   set confirmed_at = authenticated_at;

alter table sessions.sessions
    alter column confirmed_at set not null,
    add constraint sessions_confirmed_not_before_begun check (confirmed_at >= authenticated_at);

-- The third number of a version of the lifetimes: how long a proof stays fresh. Like the other two, the
-- bounds (1 to 15 minutes) are the code's and not a check here. A default is only for the versions that
-- exist, which were all made before the number was; it is five minutes, as the first of them now says.
-- Adding a column with a default writes no row, so history stays unchanged, and the trigger that refuses
-- an update never has a reason to run.
alter table sessions.lifetime_versions
    add column freshness_millis bigint not null default 300000 check (freshness_millis > 0);

alter table sessions.lifetime_versions
    alter column freshness_millis drop default;
