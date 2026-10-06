-- What happened to an account, for its owner to read (ADR-083, ADR-095): one row for each entry, only ever added.
--
-- `account_id` names an account of `identity` and is not a foreign key: a schema is one module's own
-- (ADR-059), and what becomes of a deleted account's entries is that deletion's to say. The device is a copy
-- of what it was when the entry was written, so renaming or signing out the device changes nothing here.

create schema journal;

create table journal.entries
(
    -- The order of the entries and the cursor of a page: a later entry has a larger number.
    id                bigint generated always as identity primary key,
    account_id        uuid        not null,
    kind              text        not null check (kind in (
                                      'signed_in', 'totp_turned_on', 'totp_turned_off', 'recovery_code_spent',
                                      'recovery_codes_reissued', 'other_devices_signed_out', 'guessing_stopped')),
    occurred_at       timestamptz not null,
    -- The device is known or it is not: its three parts come together, the name is the person's own.
    device_browser    text,
    device_platform   text,
    device_mobile     boolean,
    device_name       text,
    -- The session a sign-in began; only a sign-in entry has one.
    session_id        uuid,
    first_from_device boolean     not null default false,
    codes_left        integer check (codes_left >= 0),
    check ((device_browser is null) = (device_platform is null) and (device_platform is null) = (device_mobile is null)),
    check (session_id is null or kind = 'signed_in'),
    check (not first_from_device or kind = 'signed_in')
);

-- A person's page: their entries, the newest first.
create index entries_account_id on journal.entries (account_id, id desc);

-- A session was begun by one sign-in, and the entries about the second factor find its device by it.
create unique index entries_session_id on journal.entries (session_id) where session_id is not null;

-- "Has this account signed in from this kind of device before".
create index entries_account_device on journal.entries (account_id, device_browser, device_platform, device_mobile)
    where kind = 'signed_in';
