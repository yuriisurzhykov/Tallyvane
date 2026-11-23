"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Drawer } from "frontend-shared/ui/drawer";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import {
    LIFETIME_LIMIT_FIELDS,
    LifetimeFigures,
    LifetimeVersions,
    useLifetimeFaults,
    type Client,
    type LifetimeForm,
    type LifetimeVersion,
} from "@/entities/lifetime-version";
import { SaveBar, useSaveVersion } from "@/features/save-version";
import { useStrings } from "@/shared/i18n";
import { optionally } from "@/shared/lib";
import { useSpanWords } from "@/shared/span";
import { LimitRow } from "@/shared/ui/limit-row";
import { SpanInput } from "@/shared/ui/span-input";

export interface LifetimesEditorProps {
    readonly client: Client;
    /** The version the new one is started from; the drawer is open while there is one. */
    readonly from: LifetimeVersion | undefined;
    /** The version in force, whose lengths are shown beside the fields as "Now: …". */
    readonly inForce: LifetimeVersion | undefined;
    readonly onClose: () => void;
    /** A new version was kept: the list needs reading again. */
    readonly onSaved: () => void;
}

/** Keeps a new version of the session lifetimes, started from a copy of an existing one. In a drawer: creation never takes over the page (ADR-091). */
export function LifetimesEditor({ client, from, inForce, onClose, onSaved }: LifetimesEditorProps) {
    return (
        <Drawer.Root open={from !== undefined} onOpenChange={(open) => { if (!open) onClose(); }}>
            <Drawer.Popup>
                {from !== undefined ? <Body key={from.number()} client={client} from={from} inForce={inForce} onClose={onClose} onSaved={onSaved} /> : null}
            </Drawer.Popup>
        </Drawer.Root>
    );
}

interface BodyProps {
    readonly client: Client;
    readonly from: LifetimeVersion;
    readonly inForce: LifetimeVersion | undefined;
    readonly onClose: () => void;
    readonly onSaved: () => void;
}

function Body({ client, from, inForce, onClose, onSaved }: BodyProps) {
    const t = useStrings("lifetimeEditor");
    const clients = useStrings("clients");
    const refused = useStrings("faults");
    const api = useApi();
    const faults = useLifetimeFaults();
    const [form, setForm] = useState(() => from.asForm());
    const [comment, setComment] = useState("");
    const { state, save } = useSaveVersion(onSaved);
    const now = inForce?.asForm();

    const rejection = state.status === "rejected" ? state.rejection : undefined;
    const near = (field: string): string | undefined => {
        const sentences = rejection?.about(field).describe((fault) => faults.phrase(fault)) ?? [];
        return sentences.length === 0 ? undefined : sentences.join(" ");
    };
    const messages =
        rejection
            ?.except(...LIFETIME_LIMIT_FIELDS, "comment")
            .describe((fault) => faults.phrase(fault), (detail) => refused("other", { detail: detail ?? "" })) ?? [];

    return (
        <Stack gap="group-gap" className="flex-1 overflow-hidden">
            <Row gap="inline" className="items-start justify-between">
                <Stack gap="stack-tight">
                    <Drawer.Title>{t("title", { client: clients(client) })}</Drawer.Title>
                    <Drawer.Description>{t("lead", { number: from.number() })}</Drawer.Description>
                </Stack>
                <Drawer.Close label={t("close")} />
            </Row>
            <Surface variant="inset" className="p-stack">
                <Stack gap="stack-tight">
                    <Text variant="overline" color="muted">{t("result")}</Text>
                    <LifetimeFigures form={form} compact />
                </Stack>
            </Surface>
            <Stack gap="group-gap" className="flex-1 overflow-y-auto p-inline-tight">
                <LengthsEditor form={form} now={now} near={near} onChange={setForm} />
            </Stack>
            <SaveBar
                state={state}
                comment={comment}
                onComment={setComment}
                onSave={() => { save((intention) => new LifetimeVersions(api).add(client, form, comment, intention)); }}
                onCancel={onClose}
                messages={messages}
                {...optionally("commentError", near("comment"))}
            />
        </Stack>
    );
}

interface LengthsEditorProps {
    readonly form: LifetimeForm;
    /** The lengths in force, which stand beside the fields; `undefined` when none are known. */
    readonly now: LifetimeForm | undefined;
    /** What the server said is wrong with a field, in words, or nothing. */
    readonly near: (field: string) => string | undefined;
    readonly onChange: (form: LifetimeForm) => void;
}

function LengthsEditor({ form, now, near, onChange }: LengthsEditorProps) {
    const t = useStrings("lifetimeEditor");
    const span = useSpanWords();
    return (
        <Stack gap="stack">
            <LimitRow
                name={t("idle")}
                sentence={t("idleHint")}
                {...optionally("now", now?.idleSpan().describe(span.words))}
                {...optionally("error", near("idle_millis"))}
            >
                <SpanInput
                    label={t("idle")}
                    invalid={near("idle_millis") !== undefined}
                    initial={form.idleSpan()}
                    onChange={(value) => { onChange(form.withIdle(value)); }}
                    units={["minutes", "hours", "days"]}
                    unitLabels={span.unitLabels}
                    unitGroupLabel={span.unitGroupLabel}
                />
            </LimitRow>
            <LimitRow
                name={t("absolute")}
                sentence={t("absoluteHint")}
                {...optionally("now", now?.absoluteSpan().describe(span.words))}
                {...optionally("error", near("absolute_millis"))}
            >
                <SpanInput
                    label={t("absolute")}
                    invalid={near("absolute_millis") !== undefined}
                    initial={form.absoluteSpan()}
                    onChange={(value) => { onChange(form.withAbsolute(value)); }}
                    units={["hours", "days"]}
                    unitLabels={span.unitLabels}
                    unitGroupLabel={span.unitGroupLabel}
                />
            </LimitRow>
            <LimitRow
                name={t("freshness")}
                sentence={t("freshnessHint")}
                {...optionally("now", now?.freshnessSpan().describe(span.words))}
                {...optionally("error", near("freshness_millis"))}
            >
                <SpanInput
                    label={t("freshness")}
                    invalid={near("freshness_millis") !== undefined}
                    initial={form.freshnessSpan()}
                    onChange={(value) => { onChange(form.withFreshness(value)); }}
                    units={["minutes", "hours"]}
                    unitLabels={span.unitLabels}
                    unitGroupLabel={span.unitGroupLabel}
                />
            </LimitRow>
        </Stack>
    );
}
