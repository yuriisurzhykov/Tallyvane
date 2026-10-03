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
# Say which line is missing or empty, rather than letting compose fail later with a message about an anchor.
for name in BACKEND_IMAGE FRONTEND_WEB_IMAGE FRONTEND_APP_IMAGE FRONTEND_ADMIN_IMAGE POSTGRES_PASSWORD \
            TALLYVANE_HEALTH_TOKEN TOKEN_PEPPER GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET; do
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
    # Two images, one at a time (the marketing site and the admin are idle placeholders here, see
    # docker-compose.local.yml): a Next.js build is a full pnpm install, and several next to Gradle can
    # exhaust the memory Docker Desktop gives its VM, after which the daemon stops answering.
    COMPOSE_PARALLEL_LIMIT=1 "${compose[@]}" build --progress=plain server-blue frontend-app-blue
    "${compose[@]}" up -d --no-build "${services[@]}"
    # Each rebuild leaves the previous image untagged; drop those so they do not pile up.
    docker image prune -f >/dev/null
    echo "Ready: http://localhost:8080  (logs: ./stand.sh logs)"
    ;;
  down)  "${compose[@]}" down ;;
  logs)  "${compose[@]}" logs -f --tail=100 server-blue frontend-app-blue nginx ;;
  reset) "${compose[@]}" down --volumes ;;
  *) echo "usage: $0 up|down|logs|reset" >&2; exit 2 ;;
esac
