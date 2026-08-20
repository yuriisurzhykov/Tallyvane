import { useRender } from "@base-ui/react/use-render";
import { mergeProps } from "@base-ui/react/merge-props";

export type VisuallyHiddenProps = useRender.ComponentProps<"span">;

/**
 * Screen-reader-only content: present in the accessibility tree, removed from
 * the visual layout by clipping rather than `display: none` or
 * `visibility: hidden`, either of which would also remove it from assistive
 * tech. `sr-only` is Tailwind's own core utility for exactly this technique —
 * it reads from no theme scale, so it stays available even though this
 * package's `@theme` block clears every namespace token classes are named
 * from.
 */
export function VisuallyHidden({ render, className, ...props }: VisuallyHiddenProps) {
    return useRender({
        defaultTagName: "span",
        render,
        props: mergeProps<"span">({ className: "sr-only" }, { ...(className ? { className } : {}), ...props }),
    });
}
