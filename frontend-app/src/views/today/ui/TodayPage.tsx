import { AppShell } from "frontend-shared/ui/app-shell";
import { AccountMenu } from "@/widgets/account-menu";
import { HomeBoard } from "@/widgets/home-board";

const NAV_ITEMS = [
    { label: "Today", href: "/today", isActive: true },
    { label: "Résumé", href: "/resume", isActive: false },
];

/** The home of variant E: one main step, the route, the stories, and the input pinned below. */
export function TodayPage() {
    return (
        <AppShell navItems={NAV_ITEMS} title="Today" skipLinkLabel="Skip to content" actions={<AccountMenu />}>
            <HomeBoard />
        </AppShell>
    );
}
