import { registerOTel } from "@vercel/otel";

/**
 * Called once, from each app's own root `instrumentation.ts` (a Next.js convention: the file
 * must exist per app, at that exact path, or Next.js never runs it — a shared package cannot
 * stand in for it). What it does is identical across `frontend-web`, `frontend-app` and
 * `frontend-admin`, so only the one-line wrapper differs per app, not this call.
 *
 * Exported from its own subpath (`frontend-shared/lib/register-otel`), not from the `./lib`
 * barrel: `instrumentation.ts` runs server-only, and that barrel also carries client-only hooks
 * (`useDebouncedAutosave`) — importing the whole barrel from a server-only file pulls those
 * hooks into a module graph where Next.js refuses them. Measured: that is exactly the build
 * error this repository hit before this function got its own path.
 *
 * `@vercel/otel` reads `OTEL_EXPORTER_OTLP_ENDPOINT`/`OTEL_EXPORTER_OTLP_HEADERS` itself — the
 * same two variables the backend's OpenTelemetry Java agent already uses (ADR-067), one Grafana
 * Cloud project for all four services. Nothing here repeats them.
 *
 * @param serviceName Distinguishes one app's spans from another's in Grafana Cloud —
 * `tallyvane-frontend-web`, `tallyvane-frontend-app`, `tallyvane-frontend-admin`.
 */
export function registerTallyvaneOtel(serviceName: string): void {
    registerOTel({ serviceName });
}
