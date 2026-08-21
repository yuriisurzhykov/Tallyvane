import type { ReactNode } from "react";
import { Row } from "../row";
import { Stack } from "../stack";
import { Text } from "../text";

export type StatDeltaTone = "success" | "danger" | "neutral";

export interface StatDelta {
    /** Already formatted, e.g. `"+12%"` or `"−3 today"` — `Stat` lays it out, it does not compute it. */
    readonly value: string;
    /** `"success"`/`"danger"` for a real up/down, `"neutral"` for no change — deliberately narrower than the five-value `Tone` union: a delta only ever moves one of three ways. */
    readonly tone: StatDeltaTone;
}

export interface StatProps {
    readonly label: string;
    /** Typically a `Numeric` or `Money` the caller already built — `Stat` lays it out, it never formats it itself. */
    readonly value: ReactNode;
    readonly delta?: StatDelta;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Forwards `tone` straight to `Text`'s own tone resolution rather than a
 * second `Record<StatDeltaTone, string>` lookup — `COMPONENTS.md` §2's
 * "Text's variant/tone resolution counts as neither [domain nor
 * presentation knowledge]" applies here exactly the way it already does
 * for every other component that reuses `Text` for a coloured label.
 * `"neutral"` is the one case `Text`'s own tone union leaves to `color`,
 * so it is spelled out explicitly rather than relying on `Text`'s default.
 */
function DeltaText({ tone, children }: { readonly tone: StatDeltaTone; readonly children: ReactNode }) {
    return tone === "neutral" ? (
        <Text variant="small" color="secondary">
            {children}
        </Text>
    ) : (
        <Text variant="small" tone={tone}>
            {children}
        </Text>
    );
}

/**
 * Tier 1 — composes `Row`/`Stack`/`Text` (Tier 0) to lay out one large
 * number with a label and an optional delta: analytics, and the Today
 * header. Per `COMPONENTS.md` §4, this component does not format `value`
 * itself — the caller passes an already-rendered `Numeric`/`Money`, and
 * `Stat`'s own job is only where the label and delta sit around it.
 */
export function Stat({ label, value, delta, className }: StatProps) {
    return (
        <Stack gap="stack-tight" {...(className ? { className } : {})}>
            <Text variant="caption" color="secondary">
                {label}
            </Text>
            <Row gap="inline-tight">
                {value}
                {delta ? <DeltaText tone={delta.tone}>{delta.value}</DeltaText> : null}
            </Row>
        </Stack>
    );
}
