import type { ReactNode } from "react";
import { Badge } from "frontend-shared/ui/badge";
import { Logo } from "frontend-shared/ui/logo";
import { PageFrame } from "frontend-shared/ui/page-frame";
import { Row } from "frontend-shared/ui/row";
import { SidebarNav, type SidebarNavItem } from "frontend-shared/ui/sidebar-nav";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";

/** A named run of destinations in the side column. */
export interface NavGroup {
    readonly label: string;
    readonly items: readonly SidebarNavItem[];
}

export interface AdminShellProps {
    readonly groups: readonly NavGroup[];
    readonly title: string;
    /** One sentence under the title. */
    readonly lead?: string;
    /** The page's primary act, beside the title. */
    readonly action?: ReactNode;
    readonly children: ReactNode;
}

/**
 * The frame of every page of the administrators' site: the mark with the admin badge and the grouped
 * destinations at the side, and the page's heading with its one act at the head of the content.
 */
export function AdminShell({ groups, title, lead, action, children }: AdminShellProps) {
    const auth = useStrings("auth");
    const nav = useStrings("nav");

    return (
        <PageFrame
            title={title}
            skipLinkLabel={nav("skipToContent")}
            {...(lead !== undefined ? { lead } : {})}
            {...(action !== undefined ? { actions: action } : {})}
            sidebar={
                <Stack gap="group-gap" className="p-stack">
                    <Row gap="inline">
                        <Logo text={auth("productName")} mark={auth("mark")} />
                        <Badge tone="attention">{auth("adminBadge")}</Badge>
                    </Row>
                    <Stack gap="stack-tight">
                        {groups.map((group) => (
                            <Stack key={group.label} gap="stack-tight">
                                <Text variant="overline" color="muted" className="hidden lg:block">{group.label}</Text>
                                <SidebarNav items={group.items} label={group.label} />
                            </Stack>
                        ))}
                    </Stack>
                </Stack>
            }
        >
            {children}
        </PageFrame>
    );
}
