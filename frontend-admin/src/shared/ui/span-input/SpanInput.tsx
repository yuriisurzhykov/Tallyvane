"use client";

import { useState } from "react";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Toggle } from "frontend-shared/ui/toggle";
import { ToggleGroup } from "frontend-shared/ui/toggle-group";
import { Span, type SpanUnit } from "../../span";

export interface SpanInputProps {
    readonly label: string;
    readonly hint?: string;
    /** What the server said is wrong with this length, in words. */
    readonly error?: string;
    /** Where the field starts. Changes after that are the field's own: it reports them, it does not follow the parent. */
    readonly initial: Span;
    readonly onChange: (span: Span) => void;
    /** The units a person may pick, smallest sensible one included. */
    readonly units: readonly SpanUnit[];
    readonly unitLabels: Readonly<Record<SpanUnit, string>>;
    readonly unitGroupLabel: string;
}

interface Draft {
    readonly unit: SpanUnit;
    readonly text: string;
}

/** A number and the unit it is counted in, reporting the length they make. What cannot be read as a length is reported as none (zero), which the server refuses by name. */
export function SpanInput({ label, hint, error, initial, onChange, units, unitLabels, unitGroupLabel }: SpanInputProps) {
    const [draft, setDraft] = useState<Draft>(() => {
        const unit = initial.naturalUnit(units);
        return { unit, text: String(initial.amountIn(unit)) };
    });

    const change = (next: Draft) => {
        setDraft(next);
        const amount = next.text.trim() === "" ? 0 : Number(next.text);
        onChange(Span.of(Number.isFinite(amount) && amount > 0 ? amount : 0, next.unit));
    };

    return (
        <Stack gap="inline-tight">
            <Field label={label} {...(hint !== undefined ? { description: hint } : {})} {...(error !== undefined ? { error } : {})}>
                <Input
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="any"
                    value={draft.text}
                    onChange={(event) => { change({ ...draft, text: event.target.value }); }}
                />
            </Field>
            <ToggleGroup
                aria-label={unitGroupLabel}
                value={[draft.unit]}
                onValueChange={(picked) => {
                    const unit = units.find((candidate) => picked.includes(candidate));
                    if (unit !== undefined) change({ ...draft, unit });
                }}
            >
                {units.map((unit) => (
                    <Toggle key={unit} value={unit}>{unitLabels[unit]}</Toggle>
                ))}
            </ToggleGroup>
        </Stack>
    );
}
