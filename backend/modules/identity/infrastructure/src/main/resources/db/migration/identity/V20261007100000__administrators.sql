-- Who may administer the system (ADR-097): the accounts an operator gave the right to.
--
-- Rows are added by `ops/grant-admin.sh` and removed by hand; the application reads this table and never
-- writes it, so an administrator's captured session cannot make another administrator. The right goes with
-- the account when the account is deleted. It is asked afresh on every request that needs it, so taking a row
-- away works from the next request.

create table identity.admins
(
    account_id uuid        primary key references identity.accounts (id) on delete cascade,
    granted_at timestamptz not null
);
