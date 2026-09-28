"use client";

import { useState, type ComponentType, type MouseEvent, type ReactNode } from "react";
import type { SettingsSectionDefinition } from "settings-kit/entities/settings-section";
import { useSettingsNavigationGuard } from "settings-kit/features/settings-navigation";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Row } from "frontend-shared/ui/row";
import { SidebarNav } from "frontend-shared/ui/sidebar-nav";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";

export interface SettingsWorkspaceLabels {
    readonly navigation: string;
    readonly openNavigation: string;
    readonly closeNavigation: string;
}

export type SettingsWorkspaceNavigate = (href: string, afterNavigation?: () => void) => void;

export interface SettingsWorkspaceViewProps {
    readonly sections: readonly SettingsSectionDefinition[];
    readonly activeSection: SettingsSectionDefinition;
    readonly labels: SettingsWorkspaceLabels;
    readonly navigate: SettingsWorkspaceNavigate;
    readonly headingLevel?: "h1" | "h2";
    readonly children: ReactNode;
}

export interface SettingsWorkspaceProps {
    readonly sections: readonly SettingsSectionDefinition[];
    readonly activeSectionId: string;
    readonly labels: SettingsWorkspaceLabels;
    readonly children: ReactNode;
    /** Use h2 when the host shell already renders the route's h1. Defaults to h1. */
    readonly headingLevel?: "h1" | "h2";
    /** Replace the default responsive layout while keeping section and guard contracts. */
    readonly view?: ComponentType<SettingsWorkspaceViewProps>;
}

function isUnmodifiedPrimaryClick(event: MouseEvent<HTMLAnchorElement>): boolean {
    return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
}

/** Standard desktop rail and mobile left drawer; route resolution stays with the application. */
export function SettingsWorkspaceDefaultView({
    sections,
    activeSection,
    labels,
    navigate,
    headingLevel = "h1",
    children,
}: SettingsWorkspaceViewProps) {
    const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
    const navigationItems = sections.map(section => ({
        label: section.label,
        href: section.href,
        isActive: section.id === activeSection.id,
    }));
    const onDesktopNavigate = (href: string, event: MouseEvent<HTMLAnchorElement>) => {
        if (!isUnmodifiedPrimaryClick(event)) return;
        event.preventDefault();
        navigate(href);
    };
    const onMobileNavigate = (href: string, event: MouseEvent<HTMLAnchorElement>) => {
        if (!isUnmodifiedPrimaryClick(event)) return;
        event.preventDefault();
        navigate(href, () => { setMobileNavigationOpen(false); });
    };

    return (
        <Row gap="section-gap" className="items-stretch">
            <SidebarNav
                items={navigationItems}
                ariaLabel={labels.navigation}
                heading={labels.navigation}
                layout="vertical"
                activeAppearance="subtle"
                surface="inset"
                onNavigate={onDesktopNavigate}
                className="hidden w-52 shrink-0 sm:flex"
            />

            <Stack gap="section-gap" className="min-w-0 flex-1">
                <Drawer.Root
                    open={mobileNavigationOpen}
                    onOpenChange={setMobileNavigationOpen}
                    swipeDirection="left"
                >
                    <Row gap="inline" className="sm:hidden">
                        <Drawer.Trigger render={<Button tone="neutral" type="button">{labels.openNavigation}</Button>} />
                    </Row>
                    <Drawer.Popup placement="left">
                        <Row gap="inline" className="justify-between">
                            <Drawer.Title>{labels.navigation}</Drawer.Title>
                            <Drawer.Close label={labels.closeNavigation} />
                        </Row>
                        <SidebarNav
                            items={navigationItems}
                            ariaLabel={labels.navigation}
                            layout="vertical"
                            activeAppearance="subtle"
                            onNavigate={onMobileNavigate}
                        />
                    </Drawer.Popup>
                </Drawer.Root>

                <Stack as="section" gap="section-gap" aria-label={activeSection.label}>
                    <Stack gap="inline-tight">
                        <Text variant="title1" as={headingLevel}>{activeSection.label}</Text>
                        {activeSection.description
                            ? <Text variant="body" color="muted">{activeSection.description}</Text>
                            : null}
                    </Stack>
                    {children}
                </Stack>
            </Stack>
        </Row>
    );
}

/** Resolves the active route metadata and delegates layout to an injectable view. */
export function SettingsWorkspace({
    sections,
    activeSectionId,
    labels,
    children,
    headingLevel = "h1",
    view: View = SettingsWorkspaceDefaultView,
}: SettingsWorkspaceProps) {
    const { requestNavigation } = useSettingsNavigationGuard();
    const activeSection = sections.find((section) => section.id === activeSectionId);
    if (!activeSection) throw new Error(`Unknown settings section id '${activeSectionId}'.`);

    return (
        <View
            sections={sections}
            activeSection={activeSection}
            labels={labels}
            navigate={requestNavigation}
            headingLevel={headingLevel}
        >
            {children}
        </View>
    );
}
