import { Meter as BaseMeter } from "@base-ui/react/meter";

export interface MeterProps {
    /** Copy arrives as a prop below Tier 3 (`COMPONENTS.md` §12) — this component owns no words of its own. Wired to Base UI's `Meter.Label` via `aria-labelledby`. */
    readonly label: string;
    readonly value: number;
    /** @default 0, Base UI's own default. */
    readonly min?: number;
    /** @default 100, Base UI's own default. */
    readonly max?: number;
    readonly className?: string;
}

/**
 * Tier 0 — a value within a range, semantically distinct from `Progress`
 * (task completion) even though the two currently share a visual language.
 * Behaviour, `aria-valuenow`/`min`/`max`/`valuetext`, and the default
 * percentage formatting are all Base UI's own (`@base-ui/react/meter`,
 * ADR-031); this component supplies only tokens and the smaller public
 * surface (`label`, `value`, `min`, `max`) over the compound
 * `Root`/`Track`/`Indicator`/`Label`/`Value` API.
 *
 * Unlike the native HTML `<meter>`, Base UI's own `Meter` (verified against
 * the installed 1.7.0 source, not assumed) has no `low`/`high`/`optimum`
 * sub-range attributes — a value-within-a-range only, which is exactly this
 * batch's one confirmed use case, so this component adds nothing on top.
 *
 * One visual treatment, no `tone`/`size` — `COMPONENTS.md` names one use
 * case, so a variant surface beyond that would be a guess (`SKILL.md` §4,
 * YAGNI).
 */
export function Meter({ label, value, min, max, className }: MeterProps) {
    return (
        <BaseMeter.Root
            value={ value }
            { ...(min === undefined ? {} : { min }) }
            { ...(max === undefined ? {} : { max }) }
            className={ ["flex flex-col gap-inline-tight", className].filter(Boolean).join(" ") }
        >
            <div className="flex items-center justify-between gap-inline">
                <BaseMeter.Label className="text-small text-text-secondary">{ label }</BaseMeter.Label>
                <BaseMeter.Value className="text-small text-text-secondary"/>
            </div>
            {/* Same track/indicator shape as `Progress`, and the same reasoning for it — see that component's README. */ }
            <BaseMeter.Track className="relative h-inline-tight overflow-hidden rounded-pill bg-surface-inset">
                <BaseMeter.Indicator className="absolute rounded-pill bg-interactive-primary"/>
            </BaseMeter.Track>
        </BaseMeter.Root>
    );
}
