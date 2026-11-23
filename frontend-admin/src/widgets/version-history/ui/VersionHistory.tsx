"use client";

import type { ReactNode } from "react";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { EmptyState } from "frontend-shared/ui/empty-state";
import { Row } from "frontend-shared/ui/row";
import { Skeleton } from "frontend-shared/ui/skeleton";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import type { Activation, Harmless } from "@/features/activate-version";
import type { Diff } from "@/shared/diff";
import { useStrings } from "@/shared/i18n";
import type { Loaded } from "@/shared/lib";
import { InForceCard } from "./InForceCard";
import type { Listed } from "./Listed";
import { VersionList } from "./VersionList";

export interface VersionHistoryProps<Version extends Listed, Consequences extends Harmless> {
    readonly state: Loaded<readonly Version[]>;
    readonly refresh: () => void;
    /** What a version holds, drawn in full: the chain of steps and the limits, or the three lengths. */
    readonly showSummary: (version: Version) => ReactNode;
    /** How a version differs from the one in force. */
    readonly differences: (version: Version, inForce: Version) => Diff;
    readonly activationFor: (version: Version) => Activation<Consequences>;
    readonly describe: (consequences: Consequences) => readonly string[];
    readonly onNewFrom: (version: Version) => void;
}

/** The versions kept for one purpose or client: the one in force first, large, then the others, quiet, each with its acts. */
export function VersionHistory<Version extends Listed, Consequences extends Harmless>(
    props: VersionHistoryProps<Version, Consequences>,
) {
    const t = useStrings("history");
    const { state, refresh } = props;

    if (state.status === "loading") {
        return (
            <Stack gap="group-gap">
                <VisuallyHidden>{t("loading")}</VisuallyHidden>
                <Surface variant="elevated" className="p-group-gap">
                    <Stack gap="stack">
                        <Skeleton />
                        <Skeleton />
                        <Skeleton />
                    </Stack>
                </Surface>
                <Surface variant="elevated" className="p-group-gap">
                    <Stack gap="stack">
                        <Skeleton />
                        <Skeleton />
                    </Stack>
                </Surface>
            </Stack>
        );
    }
    if (state.status === "failed") {
        return (
            <Surface variant="elevated" className="p-group-gap">
                <Stack gap="stack">
                    <Callout tone="danger">{t("loadFailed")}</Callout>
                    <Row gap="inline">
                        <Button tone="neutral" size="sm" onClick={refresh}>{t("tryAgain")}</Button>
                    </Row>
                </Stack>
            </Surface>
        );
    }
    return <Kept {...props} versions={state.value} stale={state.stale} />;
}

interface KeptProps<Version extends Listed, Consequences extends Harmless> extends VersionHistoryProps<Version, Consequences> {
    readonly versions: readonly Version[];
    readonly stale: boolean;
}

function Kept<Version extends Listed, Consequences extends Harmless>({
    versions,
    stale,
    refresh,
    showSummary,
    differences,
    activationFor,
    describe,
    onNewFrom,
}: KeptProps<Version, Consequences>) {
    const t = useStrings("history");
    const inForce = versions.find((version) => version.isInForce());

    if (versions.length === 0) {
        return (
            <Surface variant="elevated" className="p-group-gap">
                <EmptyState title={t("empty")} description={t("emptyLead")} />
            </Surface>
        );
    }

    return (
        <Stack gap="group-gap">
            {stale ? (
                <Callout tone="attention">
                    <Row gap="inline" className="flex-wrap items-center">
                        {t("refreshFailed")}
                        <Button tone="ghost" size="sm" onClick={refresh}>{t("tryAgain")}</Button>
                    </Row>
                </Callout>
            ) : null}
            {inForce !== undefined ? <InForceCard version={inForce} summary={showSummary(inForce)} /> : null}
            <VersionList
                versions={versions.filter((version) => !version.isInForce())}
                inForce={inForce}
                showSummary={showSummary}
                differences={differences}
                activationFor={activationFor}
                describe={describe}
                onNewFrom={onNewFrom}
                onChanged={refresh}
            />
        </Stack>
    );
}
