"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { Toggle } from "frontend-shared/ui/toggle";
import { ToggleGroup } from "frontend-shared/ui/toggle-group";
import {
    FACTORS,
    NECESSITIES,
    POLICY_LIMIT_FIELDS,
    PolicyVersions,
    usePolicyFaults,
    type PolicyForm,
    type PolicyStep,
    type PolicyVersion,
    type Purpose,
} from "@/entities/policy-version";
import { SaveBar, useSaveVersion } from "@/features/save-version";
import { useStrings } from "@/shared/i18n";
import { optionally } from "@/shared/lib";
import { useSpanWords } from "@/shared/span";
import { SpanInput } from "@/shared/ui/span-input";

export interface PolicyEditorProps {
    readonly purpose: Purpose;
    /** The version the new one is started from; the drawer is open while there is one. */
    readonly from: PolicyVersion | undefined;
    readonly onClose: () => void;
    /** A new version was kept: the list needs reading again. */
    readonly onSaved: () => void;
}

/** Keeps a new version of a sign-in policy, started from a copy of an existing one and edited step by step. In a drawer: creation never takes over the page (ADR-091). */
export function PolicyEditor({ purpose, from, onClose, onSaved }: PolicyEditorProps) {
    return (
        <Drawer.Root open={from !== undefined} onOpenChange={(open) => { if (!open) onClose(); }}>
            <Drawer.Popup>
                {from !== undefined ? <Body key={from.number()} purpose={purpose} from={from} onClose={onClose} onSaved={onSaved} /> : null}
            </Drawer.Popup>
        </Drawer.Root>
    );
}

interface BodyProps {
    readonly purpose: Purpose;
    readonly from: PolicyVersion;
    readonly onClose: () => void;
    readonly onSaved: () => void;
}

function Body({ purpose, from, onClose, onSaved }: BodyProps) {
    const t = useStrings("policyEditor");
    const purposes = useStrings("purposes");
    const refused = useStrings("faults");
    const api = useApi();
    const faults = usePolicyFaults();
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
            ?.except(...POLICY_LIMIT_FIELDS, "comment")
            .describe((fault) => faults.phrase(fault), (detail) => refused("other", { detail: detail ?? "" })) ?? [];

    return (
        <Stack gap="group-gap">
            <Row gap="inline" className="items-start justify-between">
                <Stack gap="stack-tight">
                    <Drawer.Title>{t("title", { purpose: purposes(purpose) })}</Drawer.Title>
                    <Drawer.Description>{t("lead", { number: from.number() })}</Drawer.Description>
                </Stack>
                <Drawer.Close label={t("close")} />
            </Row>
            <StepsEditor form={form} onChange={setForm} />
            <LimitsEditor form={form} onChange={setForm} near={near} />
            <SaveBar
                state={state}
                comment={comment}
                onComment={setComment}
                onSave={() => { save((intention) => new PolicyVersions(api).add(purpose, form, comment, intention)); }}
                onCancel={onClose}
                messages={messages}
                {...optionally("commentError", near("comment"))}
            />
        </Stack>
    );
}

interface EditorPartProps {
    readonly form: PolicyForm;
    readonly onChange: (form: PolicyForm) => void;
}

function StepsEditor({ form, onChange }: EditorPartProps) {
    const t = useStrings("policyEditor");
    return (
        <Stack gap="stack">
            <Stack gap="stack-tight">
                <Text variant="bodyStrong">{t("stepsHeading")}</Text>
                <Text variant="small" color="muted">{t("stepsHint")}</Text>
            </Stack>
            {form.mapSteps((step, position) => (
                <StepEditor key={position} step={step} position={position} form={form} onChange={onChange} />
            ))}
            <Row gap="inline">
                <Button tone="neutral" size="sm" onClick={() => { onChange(form.withStepAdded()); }}>{t("addStep")}</Button>
            </Row>
        </Stack>
    );
}

interface StepEditorProps extends EditorPartProps {
    readonly step: PolicyStep;
    /** From 0; the screen numbers steps from 1. */
    readonly position: number;
}

function StepEditor({ step, position, form, onChange }: StepEditorProps) {
    const t = useStrings("policyEditor");
    const factors = useStrings("factors");
    const necessity = useStrings("necessity");
    return (
        <Surface variant="inset" className="p-stack">
            <Stack gap="stack">
                <Row gap="inline" className="justify-between">
                    <Text variant="bodyStrong">{t("step", { position: position + 1 })}</Text>
                    <Button tone="ghost" size="sm" onClick={() => { onChange(form.withoutStep(position)); }}>
                        {t("removeStep", { position: position + 1 })}
                    </Button>
                </Row>
                <Stack gap="inline-tight">
                    <Text variant="small">{t("accepts")}</Text>
                    <ToggleGroup
                        multiple
                        aria-label={t("accepts")}
                        value={FACTORS.filter((factor) => step.accepts(factor))}
                        onValueChange={(picked) => {
                            const changed = FACTORS.find((factor) => step.accepts(factor) !== picked.includes(factor));
                            if (changed !== undefined) onChange(form.withFactorToggled(position, changed));
                        }}
                        className="flex-wrap"
                    >
                        {FACTORS.map((factor) => (
                            <Toggle key={factor} value={factor}>{factors(factor)}</Toggle>
                        ))}
                    </ToggleGroup>
                </Stack>
                <Stack gap="inline-tight">
                    <Text variant="small">{t("appliesTo")}</Text>
                    <ToggleGroup
                        aria-label={t("appliesTo")}
                        value={NECESSITIES.filter((candidate) => step.appliesTo(candidate))}
                        onValueChange={(picked) => {
                            const next = NECESSITIES.find((candidate) => picked.includes(candidate) && !step.appliesTo(candidate));
                            if (next !== undefined) onChange(form.withNecessity(position, next));
                        }}
                        className="flex-wrap"
                    >
                        {NECESSITIES.map((candidate) => (
                            <Toggle key={candidate} value={candidate}>{necessity(candidate)}</Toggle>
                        ))}
                    </ToggleGroup>
                </Stack>
            </Stack>
        </Surface>
    );
}

interface LimitsEditorProps extends EditorPartProps {
    /** What the server said is wrong with a field, in words, or nothing. */
    readonly near: (field: string) => string | undefined;
}

function LimitsEditor({ form, onChange, near }: LimitsEditorProps) {
    const t = useStrings("policyEditor");
    const span = useSpanWords();
    return (
        <Stack gap="stack">
            <Text variant="bodyStrong">{t("limitsHeading")}</Text>
            <SpanInput
                label={t("attemptLifetime")}
                {...optionally("error", near("attempt_lifetime_millis"))}
                initial={form.attemptLifetime()}
                onChange={(value) => { onChange(form.withAttemptLifetime(value)); }}
                units={["minutes", "hours"]}
                unitLabels={span.unitLabels}
                unitGroupLabel={span.unitGroupLabel}
            />
            <Field label={t("maxFailures")} {...optionally("error", near("max_failures"))}>
                <Input
                    type="number"
                    inputMode="numeric"
                    min={1}
                    step={1}
                    value={String(form.maxFailures())}
                    onChange={(event) => { onChange(form.withMaxFailures(Math.max(0, Math.floor(Number(event.target.value) || 0)))); }}
                />
            </Field>
            <SpanInput
                label={t("firstDelay")}
                hint={t("firstDelayHint")}
                {...optionally("error", near("first_delay_millis"))}
                initial={form.firstDelay()}
                onChange={(value) => { onChange(form.withFirstDelay(value)); }}
                units={["seconds", "minutes"]}
                unitLabels={span.unitLabels}
                unitGroupLabel={span.unitGroupLabel}
            />
        </Stack>
    );
}
