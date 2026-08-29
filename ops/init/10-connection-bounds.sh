#!/bin/bash
# Server-side bounds on the application role, as defence in depth behind the ones
# PostgresPersistence sets on its own connections (ADR-061).
#
# These cover what a pool setting cannot: psql, the migrate command, and anything else that
# connects. A coroutine timeout cannot interrupt a blocked JDBC call, and a statement waiting
# on a lock gets no error at all until something ends it — measured in playground/isolation.
#
# A shell file rather than a .sql one because the values arrive as environment variables, and
# plain SQL run by the entrypoint cannot read them. The entrypoint runs both kinds.
#
# ALTER ROLE and not ALTER DATABASE: a database-level setting is lost by
# `CREATE DATABASE ... TEMPLATE`, measured while removing citext (ADR-059). This runs once, when
# the data directory is created, so a change here needs the volume recreated or the statements
# applied by hand — stated so nobody assumes a container restart is enough.
set -euo pipefail

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
alter role "$POSTGRES_USER" set statement_timeout = '${POSTGRES_STATEMENT_TIMEOUT}';
alter role "$POSTGRES_USER" set lock_timeout = '${POSTGRES_LOCK_TIMEOUT}';
alter role "$POSTGRES_USER" set idle_in_transaction_session_timeout = '${POSTGRES_IDLE_IN_TRANSACTION_TIMEOUT}';
SQL

echo "Applied statement/lock/idle bounds to role $POSTGRES_USER."
