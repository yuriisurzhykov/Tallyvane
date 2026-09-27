"use client";

import { useState, type ComponentType, type MouseEvent, type ReactNode } from "react";
import type { SettingsSectionDefinition } from "settings-kit/entities/settings-section";
import { useSettingsNavigationGuard } from "settings-kit/features/settings-navigation";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Link } from "frontend-shared/ui/link";
import { Row } from "frontend-shared/ui/row";
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
    readonly children: ReactNode;
}

export interface SettingsWorkspaceProps {
    readonly sections: readonly SettingsSectionDefinition[];
    readonly activeSectionId: string;
    readonly labels: SettingsWorkspaceLabels;
    readonly children: ReactNode;
    /** Replace the default responsive layout while keeping section and guard contracts. */
    readonly view?: ComponentType<SettingsWorkspaceViewProps>;
}

function isUnmodifiedPrimaryClick(event: MouseEvent<HTMLAnchorElement>): boolean {
    return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
}

function SectionLink({
    section,
    active,
    navigate,
    afterNavigation,
}: {
    readonly section: SettingsSectionDefinition;
    readonly active: boolean;
    readonly navigate: SettingsWorkspaceNavigate;
    readonly afterNavigation?: () => void;
}) {
    return (
        <Link
            href={section.href}
            className={[
                "block rounded-control px-inline py-inline-tight no-underline transition-hover focus-visible:focus-ring",
                active ? "bg-interactive-primary text-text-on-accent" : "text-text-secondary hover:bg-surface-row-hover hover:text-text-primary",
            ].join(" ")}
            {...(active ? { "aria-current": "page" as const } : {})}
            onClick={(event) => {
                if (!isUnmodifiedPrimaryClick(event)) return;
                event.preventDefault();
                navigate(section.href, afterNavigation);
            }}
        >
            {section.label}
        </Link>
    );
}

/** Standard desktop rail and mobile left drawer; route resolution stays with the application. */
export function SettingsWorkspaceDefaultView({
    sections,
    activeSection,
    labels,
    navigate,
    children,
}: SettingsWorkspaceViewProps) {
    const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);

    return (
        <Row gap="section-gap" className="items-start">
            <Stack as="aside" gap="stack" aria-label={labels.navigation} className="hidden w-(--layout-sidebar-expanded) shrink-0 lg:flex">
                <Text variant="overline" color="muted">{labels.navigation}</Text>
                <Stack as="nav" gap="stack-tight" aria-label={labels.navigation}>
                    {sections.map((section) => (
                        <SectionLink
                            key={section.id}
                            section={section}
                            active={section.id === activeSection.id}
                            navigate={navigate}
                        />
                    ))}
                </Stack>
            </Stack>

            <Stack gap="section-gap" className="min-w-0 flex-1">
                <Drawer.Root
                    open={mobileNavigationOpen}
                    onOpenChange={setMobileNavigationOpen}
                    swipeDirection="left"
                >
                    <Row gap="inline" className="lg:hidden">
                        <Drawer.Trigger render={<Button tone="neutral" type="button">{labels.openNavigation}</Button>} />
                    </Row>
                    <Drawer.Popup placement="left">
                        <Row gap="inline" className="justify-between">
                            <Drawer.Title>{labels.navigation}</Drawer.Title>
                            <Drawer.Close label={labels.closeNavigation} />
                        </Row>
                        <Stack as="nav" gap="stack-tight" aria-label={labels.navigation}>
                            {sections.map((section) => (
                                <SectionLink
                                    key={section.id}
                                    section={section}
                                    active={section.id === activeSection.id}
                                    navigate={navigate}
                                    afterNavigation={() => setMobileNavigationOpen(false)}
                                />
                            ))}
                        </Stack>
                    </Drawer.Popup>
                </Drawer.Root>

                <Stack as="section" gap="section-gap" aria-label={activeSection.label}>
                    <Stack gap="inline-tight">
                        <Text variant="title1" as="h1">{activeSection.label}</Text>
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
        >
            {children}
        </View>
    );
}
