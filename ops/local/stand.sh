#!/usr/bin/env bash
# The production stack on your machine: ./stand.sh up | down | logs | reset | admin <email> | admin --revoke <email>
# Then open http://localhost:8080 (the console) or http://127.0.0.1:8080 (the administrators' site) in Chrome or
# Firefox (Safari does not keep `__Host-` cookies on http). The two are separate hosts, so separate sessions.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
compose=(docker compose --project-directory "$here/.." --env-file "$here/.env" -f "$here/../docker-compose.yml" -f "$here/docker-compose.local.yml" --profile blue-green)
# Every colour-less upstream nginx names must exist before it starts, so all four blues come up;
# cloudflared is left out, since there is no tunnel to a laptop.
services=(nginx server-blue frontend-web-blue frontend-app-blue frontend-admin-blue)

[[ -f "$here/.env" ]] || { echo "Copy $here/env.example to $here/.env and fill in the Google client first." >&2; exit 1; }
# Say which line is missing or empty, rather than letting compose fail later with a message about an anchor.
for name in BACKEND_IMAGE FRONTEND_WEB_IMAGE FRONTEND_APP_IMAGE FRONTEND_ADMIN_IMAGE POSTGRES_PASSWORD \
            TALLYVANE_HEALTH_TOKEN TOKEN_PEPPER TOTP_KEYSET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET \
            ADMIN_ORIGIN ADMIN_HOST; do
  grep -qE "^${name}=.+" "$here/.env" || { echo "$here/.env has no value for $name (see env.example)." >&2; exit 1; }
done

case "${1:-up}" in
  up)
    # The server image packs prebuilt output (backend/Dockerfile), so build that first. Progress is
    # printed on purpose: a cold build of every module takes minutes, and silence looks like a hang.
    # The 1 GB heap in gradle.properties is sized for the VPS; a laptop can spare more, and a build
    # that spends its time collecting garbage at 1 GB is the usual reason it seems to stop.
    # --no-daemon and in-process Kotlin: the build runs in one JVM that exits when it is done. A
    # daemon (Gradle's, plus Kotlin's own, which inherits the same 3 GB heap) would otherwise stay
    # resident for hours inside a 4 GB WSL VM, and the Docker builds below would run out of room.
    # Nothing is recompiled for it: up-to-date checks, the build cache and the configuration cache
    # all live on disk, not in the daemon.
    (cd "$root/backend" && sh ./gradlew --console=plain --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=768m" -Pkotlin.compiler.execution.strategy=in-process \
      :server:installDist :migrate:installDist)
    # Three images, one at a time (the marketing site is an idle placeholder here, see
    # docker-compose.local.yml): a Next.js build is a full pnpm install, and several next to Gradle can
    # exhaust the memory Docker Desktop gives its VM, after which the daemon stops answering.
    COMPOSE_PARALLEL_LIMIT=1 "${compose[@]}" build --progress=plain server-blue frontend-app-blue frontend-admin-blue
    "${compose[@]}" up -d --no-build "${services[@]}"
    # Each rebuild leaves the previous image untagged; drop those so they do not pile up.
    docker image prune -f >/dev/null
    echo "Ready: http://localhost:8080 (console), http://127.0.0.1:8080 (administrators); logs: ./stand.sh logs"
    echo "To administer: sign in on the console once, turn TOTP on, then ./stand.sh admin you@example.com"
    ;;
  down)  "${compose[@]}" down ;;
  logs)  "${compose[@]}" logs -f --tail=100 server-blue frontend-app-blue frontend-admin-blue nginx ;;
  admin)
    # What ops/grant-admin.sh does on the server (ADR-097), against this stand's database.
    revoke=false
    if [[ "${2:-}" == "--revoke" ]]; then revoke=true; shift; fi
    email="${2:-}"
    [[ -n "$email" ]] || { echo "usage: $0 admin [--revoke] <email>" >&2; exit 2; }
    psql_in_db() { "${compose[@]}" exec -T db sh -c 'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align "$@"' sh "$@"; }
    id=$(psql_in_db --set email="$email" <<'SQL'
select id from identity.accounts where lower(email) = lower(:'email');
SQL
)
    case "$(printf '%s' "$id" | grep -c . || true)" in
      0) echo "No account is registered with $email. Sign in once on http://localhost:8080 first." >&2; exit 1 ;;
      1) ;;
      *) echo "More than one account reports $email; nothing was changed:" >&2; printf '%s\n' "$id" >&2; exit 1 ;;
    esac
    if [[ "$revoke" == true ]]; then
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
    ;;
  reset) "${compose[@]}" down --volumes ;;
  *) echo "usage: $0 up|down|logs|reset|admin [--revoke] <email>" >&2; exit 2 ;;
esac
