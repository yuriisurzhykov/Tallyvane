"use client";

import { useId, type SyntheticEvent, type KeyboardEvent, type Ref } from "react";
import { Icon } from "../icon";

export interface ComposerProps {
    /** The accessible name of the field. */
    readonly label: string;
    readonly placeholder: string;
    readonly value: string;
    readonly onChange: (value: string) => void;
    readonly onSubmit: () => void;
    /** The accessible name of the send button. */
    readonly submitLabel: string;
    /** @default false */
    readonly busy?: boolean;
    readonly maxLength?: number;
    /** The text area itself, for a screen that moves the cursor here when the person chooses to answer. */
    readonly fieldRef?: Ref<HTMLTextAreaElement>;
    readonly className?: string;
}

/**
 * Tier 1 — the one field pinned to the bottom of a screen: say what is new, then send. A pill, because it is
 * the single thing on the screen that is always there, and nothing else is pill-shaped.
 *
 * Enter sends and Shift+Enter starts a new line; the field grows with what is written. Nothing is sent while
 * it is empty or while a send is under way.
 */
export function Composer({ label, placeholder, value, onChange, onSubmit, submitLabel, busy = false, maxLength, fieldRef, className }: ComposerProps) {
    const id = useId();
    const empty = value.trim() === "";

    const submit = (event: SyntheticEvent) => {
        event.preventDefault();
        if (!empty && !busy) onSubmit();
    };
    const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
        if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) submit(event);
    };

    return (
        <form
            onSubmit={submit}
            className={[
                "flex items-end gap-inline rounded-pill border border-border-default bg-surface-elevated py-inline pr-inline pl-group-gap shadow-elevation1",
                className,
            ]
                .filter(Boolean)
                .join(" ")}
        >
            <label htmlFor={id} className="sr-only">
                {label}
            </label>
            <textarea
                id={id}
                ref={fieldRef}
                rows={1}
                value={value}
                placeholder={placeholder}
                maxLength={maxLength}
                onChange={(event) => { onChange(event.target.value); }}
                onKeyDown={keyDown}
                className="field-sizing-content max-h-[40dvh] min-h-(--control-height-md) min-w-0 flex-1 resize-none bg-transparent py-inline text-body text-text-primary outline-none placeholder:text-text-muted"
            />
            <button
                type="submit"
                aria-label={submitLabel}
                disabled={empty || busy}
                className="flex size-(--control-height-md) shrink-0 items-center justify-center rounded-control bg-interactive-primary text-text-on-accent hover:bg-interactive-primary-hover disabled:opacity-50"
            >
                <Icon name="arrowUp" />
            </button>
        </form>
    );
}
