#!/bin/bash
# Gives an account the right to administer (ADR-097): the one way anybody becomes an administrator.
#
# The application only ever reads who is one, so no request, not even one from an administrator's own
# session, can make another administrator; this script, run on the server by someone who can reach the
# database, is the whole of it.
#
#   ops/grant-admin.sh <email>          give the right to the account registered with this address
#   ops/grant-admin.sh --revoke <email> take it away again
#
# The address is how the account is found here, once. If two accounts report the same address (it is
# not unique, ADR-077) nothing is changed and both ids are listed, so the right is never given to the
# wrong person; give it by id with psql instead.
set -euo pipefail

cd "$(dirname "$0")"

revoke=false
if [ "${1:-}" = "--revoke" ]; then
    revoke=true
    shift
fi
email="${1:-}"
if [ -z "$email" ] || [ "$#" -ne 1 ]; then
    echo "usage: $0 [--revoke] <email>" >&2
    exit 2
fi

psql_in_db() {
    docker compose exec -T db sh -c 'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align "$@"' sh "$@"
}

matches=$(psql_in_db --set email="$email" <<'SQL'
select id from identity.accounts where lower(email) = lower(:'email');
SQL
)
count=$(printf '%s' "$matches" | grep -c . || true)

if [ "$count" -eq 0 ]; then
    echo "No account is registered with $email. Sign in once with Google first." >&2
    exit 1
fi
if [ "$count" -gt 1 ]; then
    echo "More than one account reports $email; nothing was changed:" >&2
    printf '%s\n' "$matches" >&2
    exit 1
fi

id="$matches"
if [ "$revoke" = true ]; then
    psql_in_db --set id="$id" <<'SQL'
delete from identity.admins where account_id = :'id';
SQL
    echo "Account $id is no longer an administrator."
else
    psql_in_db --set id="$id" <<'SQL'
insert into identity.admins (account_id, granted_at) values (:'id', now()) on conflict do nothing;
SQL
    echo "Account $id may administer."
fi
