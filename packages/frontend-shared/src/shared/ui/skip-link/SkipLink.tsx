import type { ReactNode } from "react";

export interface SkipLinkProps {
    /** The main landmark's id, e.g. `"#main-content"` — the caller (a future `AppShell`) owns the actual target, this component only jumps to whatever it is given. */
    readonly href: string;
    /** The visible-on-focus label. Copy arrives as a prop, never hardcoded here — `COMPONENTS.md` §12. */
    readonly children: ReactNode;
}

/**
 * Tier 0 — WCAG 2.4.1's bypass mechanism. One look, one job, no variants:
 * hidden via Tailwind's own `sr-only` (already proven in `VisuallyHidden`),
 * revealed by its `focus:not-sr-only` variant — no custom clip-path logic
 * needed for exactly this pattern.
 *
 * Positioned `fixed` at the corner with `z-toast`, the highest named layer
 * below `tooltip`: a skip link that appears on focus must sit above
 * whatever chrome it lands on top of, and a tooltip can still be summoned
 * from any control on any layer including a toast (`semantic/z-index.ts`),
 * so `z-toast` is the correct ceiling rather than `z-modal`/`z-popover`.
 */
export function SkipLink({ href, children }: SkipLinkProps) {
    return (
        <a
            href={href}
            className="sr-only focus:not-sr-only focus:fixed focus:top-screen-padding focus:left-screen-padding focus:z-toast focus:rounded-control focus:border focus:border-border-default focus:bg-surface-elevated focus:px-stack focus:py-inline focus:text-body-strong focus:text-text-primary focus:focus-ring"
        >
            {children}
        </a>
    );
}
