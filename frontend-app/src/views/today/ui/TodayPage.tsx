import { AppShell } from "frontend-shared/ui/app-shell";
import { TodayActions } from "@/widgets/today-actions";
import type { TodayAction } from "@/widgets/today-actions";

/**
 * Static for now — the same "static/mock is enough" scope this whole first
 * pass through the console works under. Swap for a real fetch the day a
 * backend endpoint exists to fetch from; `TodayActions` itself already
 * takes the list as a plain prop, so nothing about the widget has to change
 * when that day comes, only what supplies this array.
 */
const MOCK_ACTIONS: TodayAction[] = [
    {
        id: "1",
        title: "Follow up with Stripe — Staff Android role",
        detail: "Recruiter screen was 9 days ago. No response since your thank-you note.",
        urgency: "now",
    },
    {
        id: "2",
        title: "Prep for Anthropic onsite",
        detail: "Panel is Thursday. Review the system-design doc you drafted last week.",
        urgency: "soon",
    },
    {
        id: "3",
        title: "Update resume with the Figma-to-code project",
        detail: "Worth adding before it applies to the next two roles you're drafting.",
        urgency: "later",
    },
];

const NAV_ITEMS = [{ label: "Today", href: "/today", isActive: true }];

/**
 * Wired to `AppShell` + the real `today-actions` widget on 2026-08-28,
 * replacing the `EmptyState` placeholder — see this view's `README.md` for
 * why the console's nav lists one item today rather than the full §12.2
 * route set: every other console route is still `.gitkeep`, and a nav item
 * pointing at a route that does not exist is a broken link, not a
 * preview of one.
 */
export function TodayPage() {
    return (
        <AppShell navItems={NAV_ITEMS} title="Today" skipLinkLabel="Skip to content">
            <TodayActions actions={MOCK_ACTIONS} />
        </AppShell>
    );
}
