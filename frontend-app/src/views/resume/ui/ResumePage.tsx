import { AppShell } from "frontend-shared/ui/app-shell";
import { AccountMenu } from "@/widgets/account-menu";
import { ResumeSheet } from "@/widgets/resume-sheet";

const NAV_ITEMS = [
    { label: "Today", href: "/today", isActive: false },
    { label: "Résumé", href: "/resume", isActive: true },
];

export function ResumePage() {
    return (
        <AppShell navItems={NAV_ITEMS} title="Résumé" skipLinkLabel="Skip to content" actions={<AccountMenu />}>
            <ResumeSheet />
        </AppShell>
    );
}
