import { SidebarNav, type SidebarNavProps } from "./SidebarNav";

/** See `dot/Dot.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<SidebarNavProps> = {
    title: "Layout/SidebarNav",
    component: SidebarNav,
};
export default meta;

export const ConsoleItems: Story<SidebarNavProps> = {
    args: {
        items: [
            { label: "Today", href: "/today", isActive: true },
            { label: "Pipeline", href: "/pipeline", isActive: false },
            { label: "Contacts", href: "/contacts", isActive: false },
            { label: "Resume", href: "/resume", isActive: false },
            { label: "Analytics", href: "/analytics", isActive: false },
            { label: "Settings", href: "/settings", isActive: false },
        ],
    },
};

export const AdminItems: Story<SidebarNavProps> = {
    args: {
        items: [
            { label: "Pages", href: "/pages", isActive: false },
            { label: "Media", href: "/media", isActive: true },
            { label: "Strings", href: "/strings", isActive: false },
        ],
    },
};

export const Empty: Story<SidebarNavProps> = {
    args: { items: [] },
};
