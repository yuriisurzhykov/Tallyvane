import { Progress as BaseProgress } from "@base-ui/react/progress";

export interface ProgressProps {
    /** Copy arrives as a prop below Tier 3 (`COMPONENTS.md` §12) — this component owns no words of its own. Wired to Base UI's `Progress.Label` via `aria-labelledby`. */
    readonly label: string;
    /** Always determinate: the one real use case (`COMPONENTS.md`: "the weekly application goal") never needs Base UI's own `null`/indeterminate mode, so this component's own public type narrows it away rather than exposing a state with no call site. */
    readonly value: number;
    /** @default 100, Base UI's own default. */
    readonly max?: number;
    readonly className?: string;
}

/**
 * Tier 0 — a determinate progress bar. Behaviour, `aria-valuenow`/`min`/
 * `max`/`valuetext`, and the default percentage formatting are all Base
 * UI's own (`@base-ui/react/progress`, ADR-031); this component supplies
 * only tokens and the smaller public surface (`label`, `value`, `max`) on
 * top of the compound `Root`/`Track`/`Indicator`/`Label`/`Value` API.
 *
 * One visual treatment, no `tone`/`size` — `COMPONENTS.md` describes exactly
 * one use case, so a variant surface beyond that would be a guess, not
 * foresight (`SKILL.md` §4, YAGNI).
 */
export function Progress({ label, value, max, className }: ProgressProps) {
    return (
        <BaseProgress.Root
            value={ value }
            { ...(max === undefined ? {} : { max }) }
            className={ ["flex flex-col gap-inline-tight", className].filter(Boolean).join(" ") }
        >
            <div className="flex items-center justify-between gap-inline">
                <BaseProgress.Label className="text-small text-text-secondary">{ label }</BaseProgress.Label>
                <BaseProgress.Value className="text-small text-text-secondary"/>
            </div>
            {/*
             * `Track` carries the fixed height (`h-inline-tight`, borrowing the
             * spacing scale for a non-gap geometry value — `Dot`'s own diameter
             * precedent); `Indicator` reads it back via `height: inherit`, which
             * Base UI sets on it directly (`ProgressIndicator.js`), so no height
             * class belongs on `Indicator` itself. Base UI also sets the
             * indicator's `insetInlineStart`/width inline, so `absolute` is the
             * only positioning class this component needs to add — no `inset-*`
             * utility, which would resolve to nothing anyway under this
             * project's adapter (the bare `--spacing` multiplier a `0` value
             * would need is cleared; see `Drawer`'s own README for the same trap
             * caught the hard way).
             */ }
            <BaseProgress.Track className="relative h-inline-tight overflow-hidden rounded-pill bg-surface-inset">
                <BaseProgress.Indicator className="absolute rounded-pill bg-interactive-primary"/>
            </BaseProgress.Track>
        </BaseProgress.Root>
    );
}
