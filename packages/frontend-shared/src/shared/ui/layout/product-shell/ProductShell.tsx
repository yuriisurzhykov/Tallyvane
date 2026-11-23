import type { ReactNode } from "react";
import { SkipLink } from "../../skip-link";

const MAIN_CONTENT_ID = "main-content";

export interface ProductShellProps {
    /** The product's wordmark, usually a link home. Always shown, at the start of the header. */
    readonly brand: ReactNode;
    /** A way back, for a screen that is one step inside another. It sits before the wordmark. */
    readonly back?: ReactNode;
    /** What sits at the end of the header: a shortcut, the account. */
    readonly actions?: ReactNode;
    /** Pinned to the bottom of the screen while the page scrolls under it. */
    readonly dock?: ReactNode;
    /** The reading column is narrow; the wide one sets a document beside its notes. @default "column" */
    readonly width?: "column" | "wide";
    /** `SkipLink`'s label — a prop, because copy belongs to the caller below Tier 3. */
    readonly skipLinkLabel: string;
    readonly children: ReactNode;
}

const WIDTH_CLASS = {
    column: "max-w-(--layout-column-max-width)",
    wide: "max-w-(--layout-column-wide-max-width)",
} as const;

/**
 * Tier 2 — the product's own frame: a calm header, one centred column, and an optional dock pinned below.
 * There is no sidebar: the product is a conversation with one next step, not a console to navigate, and
 * everything that is not the next step is a quiet icon in the header.
 *
 * Owns the `#main-content` landmark the skip link jumps to, like `AppShell`, so the two frames agree on what
 * "the content" is.
 */
export function ProductShell({ brand, back, actions, dock, width = "column", skipLinkLabel, children }: ProductShellProps) {
    const column = `mx-auto w-full ${WIDTH_CLASS[width]} px-screen-padding`;
    return (
        <div className="flex min-h-dvh flex-col">
            <SkipLink href={`#${MAIN_CONTENT_ID}`}>{skipLinkLabel}</SkipLink>
            <header className={`${column} flex items-center gap-stack-tight py-stack`}>
                {back}
                <div className="flex-1">{brand}</div>
                {actions !== undefined ? <div className="flex items-center gap-inline">{actions}</div> : null}
            </header>
            <main id={MAIN_CONTENT_ID} className={`${column} flex-1 pt-stack-tight pb-section-gap`}>
                {children}
            </main>
            {dock !== undefined ? (
                <div className="sticky bottom-0 bg-linear-to-t from-surface-primary from-60% to-transparent pt-stack pb-stack">
                    <div className={column}>{dock}</div>
                </div>
            ) : null}
        </div>
    );
}
