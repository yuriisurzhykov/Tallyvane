import type { ReactNode } from "react";

export interface KeyboardKeyProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * One visual form, always a real `<kbd>` — no `render` prop. Unlike `Text`,
 * where the rendered tag genuinely varies by document context, a key cap's
 * semantics never do: it is always naming a key, so there is nothing for a
 * caller to opt into. To show a combination (e.g. "Ctrl" + "K"), the caller
 * composes two `<KeyboardKey>` instances with a separator between them —
 * this component does not take a `keys` array.
 */
const CLASS_NAME = "inline-flex items-center justify-center rounded-chip border border-border-subtle bg-surface-inset px-inline py-inline-tight text-caption text-text-secondary";

/** Renders a key combination. Required by the action menu, which must show its shortcuts. */
export function KeyboardKey({ children, className }: KeyboardKeyProps) {
    return <kbd className={className ? `${CLASS_NAME} ${className}` : CLASS_NAME}>{children}</kbd>;
}
