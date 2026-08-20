import type { ReactNode } from "react";
import { useRender } from "@base-ui/react/use-render";
import { mergeProps } from "@base-ui/react/merge-props";

export interface LinkOwnProps {
    readonly children: ReactNode;
}

/** No Base UI behaviour backs this one (`COMPONENTS.md`'s "Actions" row lists `Base: —`) — polymorphic via `useRender` directly, the same mechanism `Text`/`VisuallyHidden` already use. */
export type LinkProps = useRender.ComponentProps<"a"> & LinkOwnProps;

/**
 * Tier 0 — inline navigation text, and the `render` target when `Button`
 * must be an anchor (`<Button render={<Link href="/jobs" />} />`).
 *
 * One visual form only (YAGNI): no `tone`/`size`, since nothing in this
 * batch's known call sites needs a second one yet.
 */
export function Link({ children, render, className, ...props }: LinkProps) {
    return useRender({
        defaultTagName: "a",
        render,
        props: mergeProps<"a">(
            { className: "text-interactive-primary-text underline underline-offset-2 focus-visible:focus-ring", children },
            { ...(className ? { className } : {}), ...props },
        ),
    });
}
