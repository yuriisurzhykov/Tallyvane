import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { Track, type TrackStop } from "frontend-shared/ui/track";
import type { Home } from "@/entities/career";
import { useStrings } from "@/shared/i18n";

/** What the route shows: the skills confirmed so far, the next gap, what is ahead, and the role at the end. */
export function RouteCard({ home }: { readonly home: Home }) {
    const t = useStrings("home");
    if (home.route.length === 0) return null;
    const goal = home.goalName ?? "";
    // The stop the main step asks about, as the server chose it, not the first open one in catalog order.
    const next = home.step === "close_gap" ? home.subject : null;
    const stops: TrackStop[] = home.route.map((stop) => ({
        key: stop.skill,
        label: stop.name,
        state: stop.level === "confirmed" ? "done" : stop.skill === next ? "now" : "ahead",
    }));
    stops.push({ key: "goal", label: goal, state: "end" });
    const done = home.route.filter((stop) => stop.level === "confirmed").length;
    return (
        <Stack gap="stack-tight">
            <Text variant="overline" color="secondary">{t("headingTo", { goal })}</Text>
            <Track stops={stops} label={t("routeLabel", { goal, done, total: home.route.length })} />
        </Stack>
    );
}
