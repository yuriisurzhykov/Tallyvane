import { redirect } from "next/navigation";

/**
 * The admin surface has no home dashboard (ARCHITECTURE.md §7.4 explicitly
 * refuses a fifth admin screen until there is a second tenant) — `/` redirects
 * to the first real screen instead of 404ing, which it did until 2026-08-28.
 */
export default function AdminIndexPage() {
    redirect("/pages");
}
