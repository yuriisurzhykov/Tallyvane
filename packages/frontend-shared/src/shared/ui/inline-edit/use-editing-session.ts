import { type FocusEvent, type KeyboardEvent, useEffect, useRef, useState } from "react";

export interface UseEditingSessionResult {
    readonly isEditing: boolean;
    readonly triggerRef: React.RefObject<HTMLButtonElement | null>;
    readonly containerRef: React.RefObject<HTMLDivElement | null>;
    readonly startEditing: () => void;
    /** Saves and exits. Exposed directly (not just wired to Enter/blur below) because `renderEditor`'s own `commit` prop is this same function. */
    readonly commit: () => void;
    /** Discards and exits. Exposed directly for the same reason as `commit` — `renderEditor`'s own `cancel` prop. */
    readonly cancel: () => void;
    readonly handleKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void;
    readonly handleBlur: (event: FocusEvent<HTMLDivElement>) => void;
}

/**
 * The click-to-edit session state machine: start/commit/cancel, guarded
 * against Enter/Escape's own synchronous exit *and* the blur that unmounting
 * the focused editor triggers both reaching the same exit path, plus
 * restoring focus to the trigger once editing ends. Extracted out of
 * `InlineEdit` because this piece owns no value of its own — `onCommit` and
 * `onCancel` are where the caller keeps its own local-value/autosave
 * bookkeeping.
 */
export function useEditingSession({ onCommit, onCancel }: {
    onCommit: () => void;
    onCancel: () => void
}): UseEditingSessionResult {
    const [isEditing, setIsEditing] = useState(false);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    /** Guards against Enter/Escape's own synchronous exit *and* the blur that unmounting the focused editor triggers both reaching `commit`/`cancel` for the same exit. */
    const exitHandledRef = useRef(false);

    const wasEditingRef = useRef(isEditing);
    useEffect(() => {
        if (wasEditingRef.current && !isEditing) {
            triggerRef.current?.focus();
        }
        wasEditingRef.current = isEditing;
    }, [isEditing]);

    function startEditing() {
        exitHandledRef.current = false;
        setIsEditing(true);
    }

    function commit() {
        if (exitHandledRef.current) return;
        exitHandledRef.current = true;
        onCommit();
        setIsEditing(false);
    }

    function cancel() {
        if (exitHandledRef.current) return;
        exitHandledRef.current = true;
        onCancel();
        setIsEditing(false);
    }

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        if (event.key === "Enter") {
            event.preventDefault();
            event.stopPropagation();
            commit();
        } else if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            cancel();
        }
    }

    function handleBlur(event: FocusEvent<HTMLDivElement>) {
        const next = event.relatedTarget;
        if (next && containerRef.current?.contains(next)) return;
        commit();
    }

    return { isEditing, triggerRef, containerRef, startEditing, commit, cancel, handleKeyDown, handleBlur };
}
