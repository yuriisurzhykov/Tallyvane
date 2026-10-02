#!/usr/bin/env bash
# The production stack on your machine: ./stand.sh up | down | logs | reset
# Then open http://localhost:8080 in Chrome or Firefox (Safari does not keep `__Host-` cookies on http).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
compose=(docker compose --project-directory "$here/.." --env-file "$here/.env" -f "$here/../docker-compose.yml" -f "$here/docker-compose.local.yml" --profile blue-green)
# Every colour-less upstream nginx names must exist before it starts, so all four blues come up;
# cloudflared is left out, since there is no tunnel to a laptop.
services=(nginx server-blue frontend-web-blue frontend-app-blue frontend-admin-blue)

[[ -f "$here/.env" ]] || { echo "Copy $here/env.example to $here/.env and fill in the Google client first." >&2; exit 1; }

case "${1:-up}" in
  up)
    # The server image packs prebuilt output (backend/Dockerfile), so build that first.
    (cd "$root/backend" && ./gradlew --quiet :server:installDist :migrate:installDist)
    "${compose[@]}" up -d --build "${services[@]}"
    echo "Ready: http://localhost:8080  (logs: ./stand.sh logs)"
    ;;
  down)  "${compose[@]}" down ;;
  logs)  "${compose[@]}" logs -f --tail=100 server-blue frontend-app-blue nginx ;;
  reset) "${compose[@]}" down --volumes ;;
  *) echo "usage: $0 up|down|logs|reset" >&2; exit 2 ;;
esac
