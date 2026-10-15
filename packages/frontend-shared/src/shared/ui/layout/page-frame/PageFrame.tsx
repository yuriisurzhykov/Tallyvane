import type { ReactNode } from "react";
import { SkipLink } from "../../skip-link";
import { Text } from "../../text";

const MAIN_CONTENT_ID = "main-content";

export interface PageFrameProps {
    /** What stands at the side of the page: a brand and its navigation. The frame gives it a landmark and a column, nothing else. */
    readonly sidebar: ReactNode;
    /** The page's one heading. */
    readonly title: string;
    /** One sentence under the heading. */
    readonly lead?: string;
    /** The page's own acts, at the right of the heading. */
    readonly actions?: ReactNode;
    readonly skipLinkLabel: string;
    readonly children: ReactNode;
}

/**
 * Tier 2 — a page with a side column and a heading of its own: the shape of a screen that is read more than
 * it is operated. Where `AppShell` keeps a thin top bar for an app of many small screens, this puts the
 * title large at the head of the content, with a line of explanation and the page's acts beside it.
 * The side column is a slot: the frame owns where it sits and what landmark it is, not what is in it.
 */
export function PageFrame({ sidebar, title, lead, actions, skipLinkLabel, children }: PageFrameProps) {
    return (
        <div className="flex min-h-dvh flex-col bg-surface-inset lg:flex-row">
            <SkipLink href={`#${MAIN_CONTENT_ID}`}>{skipLinkLabel}</SkipLink>
            <aside className="border-b border-border-subtle bg-surface-primary lg:shrink-0 lg:border-r lg:border-b-0">{sidebar}</aside>
            <main id={MAIN_CONTENT_ID} className="flex-1 p-stack lg:p-section-gap">
                <div className="mx-auto flex max-w-(--layout-console-max-width) flex-col gap-section-gap">
                    <header className="flex flex-col gap-group-gap sm:flex-row sm:items-start sm:justify-between">
                        <div className="flex flex-1 flex-col gap-stack-tight">
                            <Text variant="title1" render={<h1 />}>
                                {title}
                            </Text>
                            {lead !== undefined ? (
                                <Text variant="body" color="secondary">
                                    {lead}
                                </Text>
                            ) : null}
                        </div>
                        {actions !== undefined ? <div className="flex flex-row items-center gap-inline-tight">{actions}</div> : null}
                    </header>
                    {children}
                </div>
            </main>
        </div>
    );
}
