"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Drawer } from "frontend-shared/ui/drawer";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import {
    LIFETIME_LIMIT_FIELDS,
    LifetimeVersions,
    useLifetimeFaults,
    type Client,
    type LifetimeVersion,
} from "@/entities/lifetime-version";
import { SaveBar, useSaveVersion } from "@/features/save-version";
import { useStrings } from "@/shared/i18n";
import { optionally } from "@/shared/lib";
import { useSpanWords } from "@/shared/span";
import { SpanInput } from "@/shared/ui/span-input";

export interface LifetimesEditorProps {
    readonly client: Client;
    /** The version the new one is started from; the drawer is open while there is one. */
    readonly from: LifetimeVersion | undefined;
    readonly onClose: () => void;
    /** A new version was kept: the list needs reading again. */
    readonly onSaved: () => void;
}

/** Keeps a new version of the session lifetimes, started from a copy of an existing one. In a drawer: creation never takes over the page (ADR-091). */
export function LifetimesEditor({ client, from, onClose, onSaved }: LifetimesEditorProps) {
    return (
        <Drawer.Root open={from !== undefined} onOpenChange={(open) => { if (!open) onClose(); }}>
            <Drawer.Popup>
                {from !== undefined ? <Body key={from.number()} client={client} from={from} onClose={onClose} onSaved={onSaved} /> : null}
            </Drawer.Popup>
        </Drawer.Root>
    );
}

interface BodyProps {
    readonly client: Client;
    readonly from: LifetimeVersion;
    readonly onClose: () => void;
    readonly onSaved: () => void;
}

function Body({ client, from, onClose, onSaved }: BodyProps) {
    const t = useStrings("lifetimeEditor");
    const clients = useStrings("clients");
    const refused = useStrings("faults");
    const api = useApi();
    const faults = useLifetimeFaults();
    const span = useSpanWords();
    const [form, setForm] = useState(() => from.asForm());
    const [comment, setComment] = useState("");
    const { state, save } = useSaveVersion(onSaved);

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
        <Stack gap="group-gap">
            <Row gap="inline" className="items-start justify-between">
                <Stack gap="stack-tight">
                    <Drawer.Title>{t("title", { client: clients(client) })}</Drawer.Title>
                    <Drawer.Description>{t("lead", { number: from.number() })}</Drawer.Description>
                </Stack>
                <Drawer.Close label={t("close")} />
            </Row>
            <Stack gap="stack">
                <SpanInput
                    label={t("idle")}
                    hint={t("idleHint")}
                    {...optionally("error", near("idle_millis"))}
                    initial={form.idleSpan()}
                    onChange={(value) => { setForm((now) => now.withIdle(value)); }}
                    units={["minutes", "hours", "days"]}
                    unitLabels={span.unitLabels}
                    unitGroupLabel={span.unitGroupLabel}
                />
                <SpanInput
                    label={t("absolute")}
                    hint={t("absoluteHint")}
                    {...optionally("error", near("absolute_millis"))}
                    initial={form.absoluteSpan()}
                    onChange={(value) => { setForm((now) => now.withAbsolute(value)); }}
                    units={["hours", "days"]}
                    unitLabels={span.unitLabels}
                    unitGroupLabel={span.unitGroupLabel}
                />
                <SpanInput
                    label={t("freshness")}
                    hint={t("freshnessHint")}
                    {...optionally("error", near("freshness_millis"))}
                    initial={form.freshnessSpan()}
                    onChange={(value) => { setForm((now) => now.withFreshness(value)); }}
                    units={["minutes", "hours"]}
                    unitLabels={span.unitLabels}
                    unitGroupLabel={span.unitGroupLabel}
                />
            </Stack>
            <SaveBar
                state={state}
                comment={comment}
                onComment={setComment}
                onSave={() => { save(() => new LifetimeVersions(api).add(client, form, comment)); }}
                onCancel={onClose}
                messages={messages}
                {...optionally("commentError", near("comment"))}
            />
        </Stack>
    );
}
