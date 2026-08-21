import type { ReactNode } from "react";
import { Surface, type SurfaceVariant } from "../surface";
import { Separator } from "../separator";

export interface PanelProps {
    /**
     * Proxies `Surface`'s own three variants rather than fixing one — which
     * surface level a header/body/footer card sits at (a plain card in the
     * page flow, a raised one, a recessed one) is exactly the decision
     * `Surface` already owns; `Panel` composes it, not re-decides it.
     * @default "primary"
     */
    readonly variant?: SurfaceVariant;
    /** Rendered above a divider, before `children`. Omit for a panel with no header. */
    readonly header?: ReactNode;
    /** The body. Every panel has one — a panel with nothing to show is an `EmptyState`, not an empty `Panel`. */
    readonly children: ReactNode;
    /** Rendered below a divider, after `children`. Omit for a panel with no footer. */
    readonly footer?: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Forwarded to `Surface`, never used for colour. */
    readonly className?: string;
}

const SECTION_CLASS = "p-stack";

/**
 * Tier 0 composing Tier 0 (`COMPONENTS.md` §2): `Surface` already owns
 * background, border and radius for all three of its variants, and this
 * component's only job on top is three structural slots and the dividers
 * between whichever of them are actually present — it imposes no
 * competing visual decision of its own, which is exactly the test §2 sets
 * for one Tier-0 primitive composing another.
 *
 * `header`/`footer` are plain `ReactNode` props, not compound
 * `Panel.Header`/`Panel.Footer` parts. `SKILL.md` §3.2 reserves a
 * Context-backed compound API for a component "genuinely drowning in
 * configuration," with real structural variability for its parts to
 * coordinate on — three stable, non-interacting slots in a fixed visual
 * order is precisely the "two or three stable props... wrapping that in a
 * compound API is pure ceremony" case that same section calls out.
 * `Button`'s own `leadingIcon`/`trailingIcon` sitting beside a required
 * `children` is the direct sibling precedent for "optional named slot next
 * to required content" already established in this package.
 */
export function Panel({ variant = "primary", header, children, footer, className }: PanelProps) {
    return (
        <Surface variant={ variant } { ...(className ? { className } : {}) }>
            { header !== undefined ? (
                <>
                    <div className={ SECTION_CLASS }>{ header }</div>
                    <Separator/>
                </>
            ) : null }
            <div className={ SECTION_CLASS }>{ children }</div>
            { footer !== undefined ? (
                <>
                    <Separator/>
                    <div className={ SECTION_CLASS }>{ footer }</div>
                </>
            ) : null }
        </Surface>
    );
}
