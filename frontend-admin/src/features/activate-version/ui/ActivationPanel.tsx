"use client";

import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Skeleton } from "frontend-shared/ui/skeleton";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import type { Diff } from "@/shared/diff";
import { useStrings } from "@/shared/i18n";
import { DiffBlock } from "@/shared/ui/diff-block";
import type { Activation, Harmless } from "../model/Activation";
import type { Refusal } from "../model/Refusal";
import { useActivation } from "../model/useActivation";

export interface ActivationPanelProps<Consequences extends Harmless> {
    readonly activation: Activation<Consequences>;
    readonly number: number;
    /** A rollback is an activation of an older version: the same panel, other words. */
    readonly rollback: boolean;
    /** The consequences in words, one line each. */
    readonly describe: (consequences: Consequences) => readonly string[];
    /** How the version differs from the one in force, drawn above the consequences; `undefined` when nothing is in force to compare with. */
    readonly diff?: Diff | undefined;
    /** The number of the version in force, named in the line above the differences. */
    readonly inForceNumber?: number | undefined;
    readonly onActivated: () => void;
    /** The version has gone from the server meanwhile: the list needs reading again. */
    readonly onGone: () => void;
    readonly onCancel: () => void;
}

/**
 * Opens under a version in the list, in place (the product has no modal windows, ADR-091). It shows what putting
 * the version in force would do, asks why, and does it. When the server wants a recent proof of who the person
 * is, the confirmation of `auth-kit` rises over the page by itself and the request is repeated after it.
 */
export function ActivationPanel<Consequences extends Harmless>({
    activation,
    number,
    rollback,
    describe,
    diff,
    inForceNumber,
    onActivated,
    onGone,
    onCancel,
}: ActivationPanelProps<Consequences>) {
    const t = useStrings("activation");
    const { state, confirm } = useActivation(activation, onActivated, onGone);
    const [reason, setReason] = useState("");
    const working = state.status === "confirming";

    return (
        <Surface variant="inset" className="p-stack">
            <Stack gap="stack">
                <Text variant="title3">{t(rollback ? "rollBackTitle" : "activateTitle", { number })}</Text>
                {diff !== undefined && inForceNumber !== undefined ? <Differences diff={diff} inForceNumber={inForceNumber} /> : null}
                {state.status === "previewing" ? (
                    <Stack gap="stack-tight">
                        <VisuallyHidden>{t("loading")}</VisuallyHidden>
                        <Skeleton />
                    </Stack>
                ) : null}
                {state.status === "previewFailed" ? <Callout tone="danger">{t("previewFailed")}</Callout> : null}
                {state.status === "ready" || state.status === "confirming" ? (
                    <Callout tone={state.consequences.isHarmless() ? "info" : "attention"}>
                        <Stack gap="stack-tight">
                            <Text variant="bodyStrong">{t("consequences")}</Text>
                            {describe(state.consequences).map((line) => (
                                <Text key={line} variant="body">{line}</Text>
                            ))}
                        </Stack>
                    </Callout>
                ) : null}
                {state.status === "ready" && state.refusal !== undefined ? (
                    <Callout tone="danger">{t(REFUSAL_KEY[state.refusal])}</Callout>
                ) : null}
                {state.status === "ready" || state.status === "confirming" ? (
                    <Field label={t("reasonLabel")} description={t("reasonHint")}>
                        <Input value={reason} onChange={(event) => { setReason(event.target.value); }} />
                    </Field>
                ) : null}
                <Row gap="inline" className="w-full">
                    <Button tone="neutral" size="sm" className="flex-1" disabled={working} onClick={onCancel}>{t("cancel")}</Button>
                    <Button
                        tone="primary"
                        size="sm"
                        className="flex-1"
                        loading={working}
                        disabled={state.status === "previewing" || state.status === "previewFailed"}
                        onClick={() => { confirm(reason); }}
                    >
                        {t(rollback ? "confirmRollBack" : "confirm")}
                    </Button>
                </Row>
            </Stack>
        </Surface>
    );
}

const REFUSAL_KEY = {
    declined: "failed",
    gone: "gone",
    "lock-out": "wouldLockOut",
    comment: "commentInvalid",
    failed: "failed",
} as const satisfies Record<Refusal, "failed" | "gone" | "wouldLockOut" | "commentInvalid">;

/** How the version differs from the one in force, or the sentence saying it does not. */
function Differences({ diff, inForceNumber }: { readonly diff: Diff; readonly inForceNumber: number }) {
    const t = useStrings("activation");
    return (
        <Stack gap="stack-tight">
            <Text variant="small" color="secondary">
                {t(diff.isEmpty() ? "noDifferences" : "differences", { number: inForceNumber })}
            </Text>
            {diff.isEmpty() ? null : <DiffBlock diff={diff} />}
        </Stack>
    );
}
