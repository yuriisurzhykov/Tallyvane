-- The role each person is heading for (docs/backend/08-product-core.md). One row a person; `role` is a slug of
-- the catalog, which ships with the release and is not a table. `account_id` is not a foreign key (ADR-059).

create schema career;

create table career.goals
(
    account_id uuid primary key,
    role       text not null check (length(role) between 1 and 80)
);
