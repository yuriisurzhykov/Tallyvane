import { useCallback, useEffect, useRef, useState } from "react";

export type AutosaveStatus = "idle" | "pending" | "error";

/** Per ARCHITECTURE.md §12.9: "Автосохранение через 400 мс после последнего нажатия клавиши" — 400ms after the last keystroke. */
export const DEFAULT_AUTOSAVE_DEBOUNCE_MS = 400;

export interface UseDebouncedAutosaveOptions<T> {
    readonly value: T;
    readonly onSave: (value: T) => Promise<void>;
    /** @default 400 */
    readonly debounceMs?: number;
}

export interface UseDebouncedAutosaveResult<T> {
    readonly status: AutosaveStatus;
    /** The rejection from the most recent failed save; `undefined` once a save succeeds or a new attempt starts. */
    readonly error: unknown;
    /**
     * The last value a save actually confirmed — the caller's own anchor for
     * "known good" if it wants to build a revert affordance on top of this
     * hook (see this file's own doc comment on the rollback decision below).
     */
    readonly lastSavedValue: T;
    /** Re-attempts saving the current `value` after a failed save. */
    readonly retry: () => void;
    /** Saves the current `value` now, bypassing whatever debounce wait remains. A no-op if there is nothing unsaved. */
    readonly flush: () => void;
}

/**
 * The debounce/optimistic/rollback state machine behind `InlineEdit`
 * (`shared/ui/inline-edit/`), pulled out on its own because nothing about it
 * is specific to "click to edit" — a future `Switch` that autosaves
 * immediately (`debounceMs={0}`) needs the identical pending/error/retry
 * machinery, not a text-field-shaped copy of it. Pure state, no UI, no
 * `shared/ui` import — the caller decides what "editing" or "saved" looks
 * like on screen.
 *
 * **The rollback decision.** ARCHITECTURE.md §12.9 says only "оптимистично,
 * с откатом при ошибке" (optimistic, rolling back on failure) — it does not
 * say what "rolling back" means once a save actually fails, and that is a
 * real design decision, not a detail this hook can duck. Two readings were
 * on the table: (a) this hook silently reverts to `lastSavedValue` the
 * instant a save fails, or (b) it surfaces `status: "error"` and leaves
 * `value` exactly as the caller last set it, trusting the caller to decide
 * what happens next. This hook implements (b), for a reason that is not
 * stylistic: `value` is a prop *given* to this hook, not state it owns —
 * there is nothing here to "revert", because the hook holds no source of
 * truth of its own to substitute in its place. Reverting would mean this
 * hook silently calling the caller's own setter behind its back (it cannot
 * — no setter is passed in), or lying about `value` in its return (it does
 * not return one). (a) would only be honest if this hook owned the value
 * (`useState` inside it, returning `[value, setValue]`), which was
 * considered and rejected: `InlineEdit` itself already needs to hold the
 * live draft during editing (to seed the editor, to restore it on
 * Escape) — a second, competing copy inside this hook would be the same
 * state twice, fighting over which one is authoritative the moment they
 * disagree. Surfacing the failure and handing back `lastSavedValue` is the
 * option that is honest about which module actually owns the value.
 */
export function useDebouncedAutosave<T>({ value, onSave, debounceMs = DEFAULT_AUTOSAVE_DEBOUNCE_MS }: UseDebouncedAutosaveOptions<T>): UseDebouncedAutosaveResult<T> {
    const [status, setStatus] = useState<AutosaveStatus>("idle");
    const [error, setError] = useState<unknown>(undefined);

    const valueRef = useRef(value);
    valueRef.current = value;
    const onSaveRef = useRef(onSave);
    onSaveRef.current = onSave;
    const lastSavedRef = useRef(value);
    const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    /** Lets a superseded attempt's own `.then` tell it is no longer the one whose result should reach `status`/`error` — a newer value, a `retry`, or a `flush` may have started a fresher attempt first. */
    const attemptIdRef = useRef(0);
    const isMountedRef = useRef(true);
    useEffect(() => () => {
        isMountedRef.current = false;
    }, []);

    const attemptSave = useCallback(() => {
        const thisAttempt = ++attemptIdRef.current;
        const valueToSave = valueRef.current;
        setStatus("pending");
        setError(undefined);
        onSaveRef.current(valueToSave).then(
            () => {
                if (!isMountedRef.current || attemptIdRef.current !== thisAttempt) return;
                lastSavedRef.current = valueToSave;
                setStatus("idle");
            },
            (thrown: unknown) => {
                if (!isMountedRef.current || attemptIdRef.current !== thisAttempt) return;
                setStatus("error");
                setError(thrown);
            },
        );
    }, []);

    /**
     * Depends on `[value, debounceMs, attemptSave]`, never `onSave` directly
     * — `onSaveRef` above is how the latest callback reaches `attemptSave`
     * without resetting the pending timer just because a caller re-rendered
     * with a fresh inline function, the same reasoning `SearchField.tsx`'s
     * own debounce effect already documents.
     */
    useEffect(() => {
        if (Object.is(value, lastSavedRef.current)) {
            // Back to (or still at) the last confirmed value — nothing to
            // debounce, and a stale "pending"/"error" from an edit the
            // caller itself reverted should not linger.
            setStatus("idle");
            setError(undefined);
            return;
        }
        setStatus("pending");
        setError(undefined);
        timeoutRef.current = setTimeout(attemptSave, debounceMs);
        return () => { clearTimeout(timeoutRef.current); };
    }, [value, debounceMs, attemptSave]);

    const retry = useCallback(() => {
        clearTimeout(timeoutRef.current);
        attemptSave();
    }, [attemptSave]);

    const flush = useCallback(() => {
        if (Object.is(valueRef.current, lastSavedRef.current)) return;
        clearTimeout(timeoutRef.current);
        attemptSave();
    }, [attemptSave]);

    return { status, error, lastSavedValue: lastSavedRef.current, retry, flush };
}
