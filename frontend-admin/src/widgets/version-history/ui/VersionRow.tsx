import type { ReactNode } from "react";
import { useViewer } from "auth-kit/entities/viewer";
import { Button } from "frontend-shared/ui/button";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { ActivationPanel, type Activation, type Harmless } from "@/features/activate-version";
import type { Diff } from "@/shared/diff";
import { useStrings } from "@/shared/i18n";
import type { Listed } from "./Listed";
import { LOCALE, useAuthorWords } from "./authorWords";

export interface VersionRowProps<Consequences extends Harmless> {
    readonly version: Listed;
    /** What the version holds, drawn in full; shown when the row is opened. */
    readonly summary: ReactNode;
    /** Putting this version in force goes back to an older one than the one in force. */
    readonly rollback: boolean;
    /** How the version differs from the one in force; `undefined` when nothing is in force to compare with. */
    readonly diff: Diff | undefined;
    readonly inForceNumber: number | undefined;
    readonly activation: Activation<Consequences>;
    readonly describe: (consequences: Consequences) => readonly string[];
    readonly detailsOpen: boolean;
    readonly onToggleDetails: () => void;
    readonly activationOpen: boolean;
    readonly onOpenActivation: () => void;
    readonly onCloseActivation: () => void;
    /** The version was put in force, or has gone: the list needs reading again. */
    readonly onChanged: () => void;
    readonly onNewFromThis: () => void;
}

/** A version that is not in force: quiet, with the same two acts in the same two widths in every row. */
export function VersionRow<Consequences extends Harmless>({
    version,
    summary,
    rollback,
    diff,
    inForceNumber,
    activation,
    describe,
    detailsOpen,
    onToggleDetails,
    activationOpen,
    onOpenActivation,
    onCloseActivation,
    onChanged,
    onNewFromThis,
}: VersionRowProps<Consequences>) {
    const t = useStrings("history");
    const viewer = useViewer();
    const authorWords = useAuthorWords();
    const made = version.made();
    const reason = made.reason();

    return (
        <Stack gap="stack">
            <Stack gap="stack-tight" className="sm:flex-row sm:items-start sm:justify-between">
                <Row gap="group-gap" className="flex-1 items-start">
                    <Text variant="numeric" color="secondary" className="shrink-0">{t("versionNumber", { number: version.number() })}</Text>
                    <Stack gap="inline-tight" className="flex-1">
                        <Text variant="bodyStrong">{reason ?? t("initialVersion")}</Text>
                        <Text variant="small" color="muted">
                            {t("madeBy", { by: made.author(authorWords, viewer), when: made.when(LOCALE) })}
                        </Text>
                    </Stack>
                </Row>
                <Row gap="inline-tight" className="w-full sm:w-1/3 sm:shrink-0">
                    <Button tone="ghost" size="sm" className="flex-1" aria-expanded={detailsOpen} onClick={onToggleDetails}>
                        {t(detailsOpen ? "hideDetails" : "details")}
                    </Button>
                    <Button tone="neutral" size="sm" className="flex-2" onClick={onOpenActivation}>
                        {t(rollback ? "rollBack" : "activate")}
                    </Button>
                </Row>
            </Stack>
            {detailsOpen ? (
                <Surface variant="inset" className="p-stack">
                    <Stack gap="stack">
                        {summary}
                        <Row gap="inline">
                            <Button tone="neutral" size="sm" onClick={onNewFromThis}>{t("newFromThis")}</Button>
                        </Row>
                    </Stack>
                </Surface>
            ) : null}
            {activationOpen ? (
                <ActivationPanel
                    activation={activation}
                    number={version.number()}
                    rollback={rollback}
                    describe={describe}
                    diff={diff}
                    inForceNumber={inForceNumber}
                    onActivated={onChanged}
                    onGone={onChanged}
                    onCancel={onCloseActivation}
                />
            ) : null}
        </Stack>
    );
}
