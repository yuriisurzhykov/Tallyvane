import type { ReactNode } from "react";
import { useRender } from "@base-ui/react/use-render";
import { mergeProps } from "@base-ui/react/merge-props";

export type SpacingRole = "inline-tight" | "inline" | "stack-tight" | "stack" | "group-gap" | "section-gap";

const GAP_CLASS_NAME: Record<SpacingRole, string> = {
    "inline-tight": "gap-inline-tight",
    inline: "gap-inline",
    "stack-tight": "gap-stack-tight",
    stack: "gap-stack",
    "group-gap": "gap-group-gap",
    "section-gap": "gap-section-gap",
};

export interface StackProps {
    /** No default — forces a deliberate choice of role at every call site. */
    readonly gap: SpacingRole;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
    /** The semantic block element. Defaults to `div`. */
    readonly as?: "article" | "aside" | "div" | "footer" | "header" | "li" | "main" | "nav" | "section" | "ul";
    readonly id?: string;
    readonly role?: string;
    readonly "aria-label"?: string;
    readonly "aria-labelledby"?: string;
}

/** Tier 0 — vertical flow. Gaps only from the spacing roles; there is no other way to space children apart. */
export function Stack({ gap, children, className, as = "div", ...props }: StackProps) {
    return useRender({
        defaultTagName: as,
        props: mergeProps<"div">(
            { className: ["flex flex-col", GAP_CLASS_NAME[gap]].join(" "), children },
            { ...(className ? { className } : {}), ...props },
        ),
    });
}
