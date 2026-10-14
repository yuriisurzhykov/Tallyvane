"use client";

import { useState } from "react";
import { useViewer } from "auth-kit/entities/viewer";
import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { EmptyState } from "frontend-shared/ui/empty-state";
import { Row } from "frontend-shared/ui/row";
import { Skeleton } from "frontend-shared/ui/skeleton";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import { ActivationPanel, type Activation, type Harmless } from "@/features/activate-version";
import type { VersionStamp } from "@/shared/history";
import { useStrings } from "@/shared/i18n";
import type { Loaded } from "@/shared/lib";

const LOCALE = "en";

/** What the list asks of any kept version, whatever it holds: a policy or a set of lifetimes. */
export interface Listed {
    number(): number;
    isInForce(): boolean;
    made(): VersionStamp;
    inForce(): VersionStamp | undefined;
}

export interface VersionHistoryProps<Version extends Listed, Consequences extends Harmless> {
    readonly state: Loaded<readonly Version[]>;
    readonly refresh: () => void;
    /** One line saying what the version holds. */
    readonly summarize: (version: Version) => string;
    readonly activationFor: (version: Version) => Activation<Consequences>;
    readonly describe: (consequences: Consequences) => readonly string[];
    readonly onNewFrom: (version: Version) => void;
}

/** The versions kept for one purpose or client: who made each and why, which is in force, and the acts on each. */
export function VersionHistory<Version extends Listed, Consequences extends Harmless>(
    props: VersionHistoryProps<Version, Consequences>,
) {
    const t = useStrings("history");
    const { state, refresh } = props;

    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="group-gap">
                <Stack gap="stack-tight">
                    <Text variant="title3">{t("title")}</Text>
                    <Text variant="body" color="secondary">{t("lead")}</Text>
                </Stack>
                {state.status === "loading" ? (
                    <Stack gap="stack">
                        <VisuallyHidden>{t("loading")}</VisuallyHidden>
                        <Skeleton className="h-16 w-full" />
                        <Skeleton className="h-16 w-full" />
                    </Stack>
                ) : null}
                {state.status === "failed" ? (
                    <Stack gap="stack">
                        <Callout tone="danger">{t("loadFailed")}</Callout>
                        <Row gap="inline">
                            <Button tone="neutral" onClick={refresh}>{t("tryAgain")}</Button>
                        </Row>
                    </Stack>
                ) : null}
                {state.status === "ready" ? (
                    <Rows {...props} versions={state.value} stale={state.stale} />
                ) : null}
            </Stack>
        </Surface>
    );
}

interface RowsProps<Version extends Listed, Consequences extends Harmless> extends VersionHistoryProps<Version, Consequences> {
    readonly versions: readonly Version[];
    readonly stale: boolean;
}

function Rows<Version extends Listed, Consequences extends Harmless>({ versions, stale, refresh, ...rest }: RowsProps<Version, Consequences>) {
    const t = useStrings("history");
    const [opened, setOpened] = useState<number | undefined>(undefined);
    const inForce = versions.find((version) => version.isInForce())?.number();

    if (versions.length === 0) {
        return <EmptyState title={t("empty")} />;
    }

    return (
        <Stack gap="stack">
            {stale ? (
                <Callout tone="attention">
                    <Row gap="inline" className="flex-wrap items-center">
                        {t("refreshFailed")}
                        <Button tone="ghost" size="sm" onClick={refresh}>{t("tryAgain")}</Button>
                    </Row>
                </Callout>
            ) : null}
            {versions.map((version) => (
                <VersionRow
                    key={version.number()}
                    version={version}
                    rollback={inForce !== undefined && version.number() < inForce}
                    isOpen={opened === version.number()}
                    onOpen={() => { setOpened(version.number()); }}
                    onClose={() => { setOpened(undefined); }}
                    onChanged={() => { setOpened(undefined); refresh(); }}
                    {...rest}
                />
            ))}
        </Stack>
    );
}

interface VersionRowProps<Version extends Listed, Consequences extends Harmless>
    extends Pick<VersionHistoryProps<Version, Consequences>, "summarize" | "activationFor" | "describe" | "onNewFrom"> {
    readonly version: Version;
    /** Putting this version in force goes back to an older one than the one in force. */
    readonly rollback: boolean;
    readonly isOpen: boolean;
    readonly onOpen: () => void;
    readonly onClose: () => void;
    /** The version was put in force, or has gone: the list needs reading again. */
    readonly onChanged: () => void;
}

function VersionRow<Version extends Listed, Consequences extends Harmless>({
    version,
    rollback,
    isOpen,
    onOpen,
    onClose,
    onChanged,
    summarize,
    activationFor,
    describe,
    onNewFrom,
}: VersionRowProps<Version, Consequences>) {
    const t = useStrings("history");
    const viewer = useViewer();
    const number = version.number();
    const made = version.made();
    const now = version.inForce();
    const reason = made.reason();
    const authorWords = { you: t("you"), migration: t("migration"), account: (id: string) => t("account", { id }) };

    return (
        <Stack gap="stack" className="border-b border-border-subtle pb-stack last:border-b-0 last:pb-0">
            <Row gap="group-gap" className="flex-wrap items-start justify-between">
                <Row gap="group-gap" className="min-w-0 flex-1 items-start">
                    <Text variant="numeric" color="secondary">{t("versionNumber", { number })}</Text>
                    <Stack gap="stack-tight" className="min-w-0 flex-1">
                        <Row gap="inline" className="flex-wrap items-center">
                            <Text variant="body">{summarize(version)}</Text>
                            {now !== undefined ? <Badge tone="success">{t("inForce")}</Badge> : null}
                        </Row>
                        <Text variant="small" color="muted">
                            {t("madeBy", { by: made.author(authorWords, viewer), when: made.when(LOCALE) })}
                        </Text>
                        {reason !== undefined ? <Text variant="small" color="secondary">{t("commentLabel", { comment: reason })}</Text> : null}
                        {now !== undefined ? (
                            <Text variant="small" color="muted">
                                {t("inForceNote", { when: now.when(LOCALE), by: now.author(authorWords, viewer) })}
                                {now.reason() !== undefined ? ` ${t("inForceReason", { reason: now.reason() ?? "" })}` : ""}
                            </Text>
                        ) : null}
                    </Stack>
                </Row>
                <Row gap="inline" className="flex-wrap">
                    <Button tone="ghost" size="sm" onClick={() => { onNewFrom(version); }}>{t("newFromThis")}</Button>
                    {now === undefined ? (
                        <Button tone="neutral" size="sm" onClick={onOpen}>{t(rollback ? "rollBack" : "activate")}</Button>
                    ) : null}
                </Row>
            </Row>
            {isOpen ? (
                <ActivationPanel
                    activation={activationFor(version)}
                    number={number}
                    rollback={rollback}
                    describe={describe}
                    onActivated={onChanged}
                    onGone={onChanged}
                    onCancel={onClose}
                />
            ) : null}
        </Stack>
    );
}
