"use client";

import { Fragment, useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Collapsible } from "frontend-shared/ui/collapsible";
import { Drawer } from "frontend-shared/ui/drawer";
import { Input } from "frontend-shared/ui/input";
import { Radio } from "frontend-shared/ui/radio";
import { RadioGroup } from "frontend-shared/ui/radio-group";
import { Row } from "frontend-shared/ui/row";
import { Separator } from "frontend-shared/ui/separator";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Switch } from "frontend-shared/ui/switch";
import { Text } from "frontend-shared/ui/text";
import {
    FACTORS,
    NECESSITIES,
    POLICY_LIMIT_FIELDS,
    PolicyFlow,
    PolicyVersions,
    usePolicyFaults,
    usePolicyWords,
    type Necessity,
    type PolicyForm,
    type PolicyStep,
    type PolicyVersion,
    type Purpose,
} from "@/entities/policy-version";
import { SaveBar, useSaveVersion } from "@/features/save-version";
import { useStrings } from "@/shared/i18n";
import { optionally } from "@/shared/lib";
import { useSpanWords } from "@/shared/span";
import { LimitRow } from "@/shared/ui/limit-row";
import { SpanInput } from "@/shared/ui/span-input";

export interface PolicyEditorProps {
    readonly purpose: Purpose;
    /** The version the new one is started from; the drawer is open while there is one. */
    readonly from: PolicyVersion | undefined;
    /** The version in force, whose limits are shown beside the fields as "Now: …". */
    readonly inForce: PolicyVersion | undefined;
    readonly onClose: () => void;
    /** A new version was kept: the list needs reading again. */
    readonly onSaved: () => void;
}

/** Keeps a new version of a sign-in policy, started from a copy of an existing one and edited step by step. In a drawer: creation never takes over the page (ADR-091). */
export function PolicyEditor({ purpose, from, inForce, onClose, onSaved }: PolicyEditorProps) {
    return (
        <Drawer.Root open={from !== undefined} onOpenChange={(open) => { if (!open) onClose(); }}>
            <Drawer.Popup>
                {from !== undefined ? <Body key={from.number()} purpose={purpose} from={from} inForce={inForce} onClose={onClose} onSaved={onSaved} /> : null}
            </Drawer.Popup>
        </Drawer.Root>
    );
}

interface BodyProps {
    readonly purpose: Purpose;
    readonly from: PolicyVersion;
    readonly inForce: PolicyVersion | undefined;
    readonly onClose: () => void;
    readonly onSaved: () => void;
}

function Body({ purpose, from, inForce, onClose, onSaved }: BodyProps) {
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
        <Stack gap="group-gap" className="flex-1 overflow-hidden">
            <Row gap="inline" className="items-start justify-between">
                <Stack gap="stack-tight">
                    <Drawer.Title>{t("title", { purpose: purposes(purpose) })}</Drawer.Title>
                    <Drawer.Description>{t("lead", { number: from.number() })}</Drawer.Description>
                </Stack>
                <Drawer.Close label={t("close")} />
            </Row>
            <Surface variant="inset" className="p-stack">
                <Stack gap="stack-tight">
                    <Text variant="overline" color="muted">{t("result")}</Text>
                    <PolicyFlow form={form} compact />
                </Stack>
            </Surface>
            <Stack gap="group-gap" className="flex-1 overflow-y-auto p-inline-tight">
                <StepsEditor form={form} onChange={setForm} />
                <LimitsEditor form={form} now={inForce?.asForm()} onChange={setForm} near={near} />
            </Stack>
            <SaveBar
                state={state}
                comment={comment}
                onComment={setComment}
                onSave={() => { save((intention) => new PolicyVersions(api).add(purpose, form, comment, intention)); }}
                onCancel={onClose}
                messages={messages}
                label={t("reasonHeading")}
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
            <Stack gap="inline-tight">
                <Text variant="title3">{t("stepsHeading")}</Text>
                <Text variant="small" color="muted">{t("stepsHint")}</Text>
            </Stack>
            {form.mapSteps((step, position) => (
                <Fragment key={position}>
                    {position > 0 ? <Text variant="overline" color="muted">{t("then")}</Text> : null}
                    <StepEditor step={step} position={position} form={form} onChange={onChange} />
                </Fragment>
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
    const hints = useStrings("factorHints");
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
                <Stack gap="stack-tight">
                    <Stack gap="inline-tight">
                        <Text variant="small">{t("accepts")}</Text>
                        <Text variant="caption" color="muted">{t("acceptsHint")}</Text>
                    </Stack>
                    <Stack gap="stack-tight">
                        {FACTORS.map((factor) => (
                            <Row key={factor} gap="group-gap" className="justify-between">
                                <Stack gap="inline-tight" className="flex-1">
                                    <Text variant="body">{factors(factor)}</Text>
                                    <Text variant="caption" color="muted">{hints(factor)}</Text>
                                </Stack>
                                <Row gap="inline">
                                    {step.accepts(factor) ? (
                                        <Text variant="caption" tone="success">{t("accepted")}</Text>
                                    ) : (
                                        <Text variant="caption" color="muted">{t("notAccepted")}</Text>
                                    )}
                                    <Switch
                                        aria-label={factors(factor)}
                                        checked={step.accepts(factor)}
                                        onCheckedChange={() => { onChange(form.withFactorToggled(position, factor)); }}
                                    />
                                </Row>
                            </Row>
                        ))}
                    </Stack>
                </Stack>
                <Separator decorative />
                <Stack gap="stack-tight">
                    <Text variant="small">{t("appliesTo")}</Text>
                    <RadioGroup<Necessity>
                        aria-label={t("appliesTo")}
                        value={NECESSITIES.find((candidate) => step.appliesTo(candidate))}
                        onValueChange={(next) => { onChange(form.withNecessity(position, next)); }}
                    >
                        {NECESSITIES.map((candidate) => (
                            <Row key={candidate} gap="inline">
                                <Radio value={candidate} aria-label={necessity(candidate)} />
                                <Text variant="body">{necessity(candidate)}</Text>
                            </Row>
                        ))}
                    </RadioGroup>
                </Stack>
            </Stack>
        </Surface>
    );
}

interface LimitsEditorProps extends EditorPartProps {
    /** The policy in force, whose limits stand beside the fields; `undefined` when none is known. */
    readonly now: PolicyForm | undefined;
    /** What the server said is wrong with a field, in words, or nothing. */
    readonly near: (field: string) => string | undefined;
}

function LimitsEditor({ form, now, onChange, near }: LimitsEditorProps) {
    const t = useStrings("policyEditor");
    const span = useSpanWords();
    const [chosen, setChosen] = useState(false);
    const faulted = POLICY_LIMIT_FIELDS.some((field) => near(field) !== undefined);
    const open = chosen || faulted;
    const words = usePolicyWords();
    return (
        <Stack gap="stack">
            <Text variant="title3">{t("limitsHeading")}</Text>
            <Collapsible.Root open={open} onOpenChange={setChosen}>
                <Stack gap="stack">
                    <Row gap="group-gap" className="flex-wrap justify-between">
                        <Text variant="numeric" color="secondary">{form.limitsLine(words)}</Text>
                        <Collapsible.Trigger className="border border-border-default">
                            {t(open ? "hideLimits" : "changeLimits")}
                        </Collapsible.Trigger>
                    </Row>
                    <Collapsible.Panel keepMounted>
                        <Stack gap="stack" className="p-inline-tight">
                            <LimitRow
                                name={t("attemptLifetime")}
                                sentence={t("attemptLifetimeHint")}
                                {...optionally("now", now?.attemptLifetime().describe(span.words))}
                                {...optionally("error", near("attempt_lifetime_millis"))}
                            >
                                <SpanInput
                                    label={t("attemptLifetime")}
                                    invalid={near("attempt_lifetime_millis") !== undefined}
                                    initial={form.attemptLifetime()}
                                    onChange={(value) => { onChange(form.withAttemptLifetime(value)); }}
                                    units={["minutes", "hours"]}
                                    unitLabels={span.unitLabels}
                                    unitGroupLabel={span.unitGroupLabel}
                                />
                            </LimitRow>
                            <LimitRow
                                name={t("maxFailures")}
                                sentence={t("maxFailuresHint")}
                                {...optionally("now", now === undefined ? undefined : String(now.maxFailures()))}
                                {...optionally("error", near("max_failures"))}
                            >
                                <Input
                                    aria-label={t("maxFailures")}
                                    aria-invalid={near("max_failures") !== undefined || undefined}
                                    type="number"
                                    inputMode="numeric"
                                    min={1}
                                    step={1}
                                    value={String(form.maxFailures())}
                                    onChange={(event) => { onChange(form.withMaxFailures(Math.max(0, Math.floor(Number(event.target.value) || 0)))); }}
                                />
                            </LimitRow>
                            <LimitRow
                                name={t("firstDelay")}
                                sentence={t("firstDelayHint")}
                                {...optionally("now", now?.firstDelay().describe(span.words))}
                                {...optionally("error", near("first_delay_millis"))}
                            >
                                <SpanInput
                                    label={t("firstDelay")}
                                    invalid={near("first_delay_millis") !== undefined}
                                    initial={form.firstDelay()}
                                    onChange={(value) => { onChange(form.withFirstDelay(value)); }}
                                    units={["seconds", "minutes"]}
                                    unitLabels={span.unitLabels}
                                    unitGroupLabel={span.unitGroupLabel}
                                />
                            </LimitRow>
                        </Stack>
                    </Collapsible.Panel>
                </Stack>
            </Collapsible.Root>
        </Stack>
    );
}
