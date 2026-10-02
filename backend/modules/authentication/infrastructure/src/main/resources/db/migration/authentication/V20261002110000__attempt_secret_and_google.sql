-- What a first Google sign-in needs kept (slice 3, ADR-087).
--
-- An attempt is found by a secret the browser holds in a cookie, and the database holds only the keyed
-- digest of it. The attempts that exist were found by their own id, which nobody holds a secret for;
-- they are minutes old by design, so they are deleted rather than given a digest nobody can present.
delete
from authentication.attempts;

alter table authentication.attempts
    add column secret_digest bytea not null,
    add column pepper_version integer not null check (pepper_version >= 1);

create unique index attempts_secret_digest on authentication.attempts (secret_digest);

-- Whose account a factor proved, as its provider names them: Google's `sub`. Only a factor that
-- identifies an account has one.
alter table authentication.attempt_verified_factors
    add column subject text;

alter table authentication.attempt_verified_factors
    add constraint verified_factor_names_whose
        check ((kind = 'google') = (subject is not null and length(btrim(subject)) > 0));

-- The three secrets of one trip to Google. Single use: the row is deleted when the reply is taken, and
-- goes with its attempt otherwise.
create table authentication.google_handshakes
(
    attempt_id uuid primary key references authentication.attempts (id) on delete cascade,
    state      text not null check (length(state) > 0),
    nonce      text not null check (length(nonce) > 0),
    verifier   text not null check (length(verifier) > 0)
);

-- What Google said about a person who has no account yet, kept for the welcome screen. Personal data
-- of somebody who has agreed to nothing, so it lives exactly as long as the attempt does.
create table authentication.google_profiles
(
    attempt_id   uuid primary key references authentication.attempts (id) on delete cascade,
    display_name text not null,
    email        text not null
);
