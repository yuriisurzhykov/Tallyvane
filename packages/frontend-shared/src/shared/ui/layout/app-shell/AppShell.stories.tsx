import { AppShell, type AppShellProps } from "./AppShell";
import { Text } from "../../text";

/** See `dot/Dot.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<AppShellProps> = {
    title: "Layout/AppShell",
    component: AppShell,
};
export default meta;

export const Console: Story<AppShellProps> = {
    args: {
        navItems: [
            { label: "Today", href: "/today", isActive: true },
            { label: "Pipeline", href: "/pipeline", isActive: false },
            { label: "Contacts", href: "/contacts", isActive: false },
            { label: "Resume", href: "/resume", isActive: false },
            { label: "Analytics", href: "/analytics", isActive: false },
            { label: "Settings", href: "/settings", isActive: false },
        ],
        title: "Today",
        skipLinkLabel: "Skip to content",
        children: <Text variant="body">Screen content renders here.</Text>,
    },
};

export const Admin: Story<AppShellProps> = {
    args: {
        navItems: [
            { label: "Pages", href: "/pages", isActive: false },
            { label: "Media", href: "/media", isActive: true },
            { label: "Strings", href: "/strings", isActive: false },
        ],
        title: "Media",
        skipLinkLabel: "Skip to content",
        children: <Text variant="body">Screen content renders here.</Text>,
    },
};
