import { useRef, useState, type ReactNode } from "react";
import { cn, useDebouncedAutosave } from "../../lib";
import { VisuallyHidden } from "../visually-hidden";
import { useEditingSession } from "./use-editing-session";

export interface InlineEditEditorRenderProps<T> {
    readonly value: T;
    readonly onChange: (value: T) => void;
    /** Saves `value` immediately (bypassing the remaining debounce) and returns to the display state. Wired to Enter and to the editor losing focus. */
    readonly commit: () => void;
    /** Restores the value the field had before this edit began, with no save, and returns to the display state. Wired to Escape. */
    readonly cancel: () => void;
}

export interface InlineEditProps<T> {
    readonly value: T;
    readonly onSave: (value: T) => Promise<void>;
    /**
     * The failure channel: `InlineEdit` does not assume a `ToastRegion` is
     * mounted somewhere above it in a tree it cannot see from Tier 1 (per
     * `COMPONENTS.md` §2's "no domain noun" and this component's own plan).
     * `retry` re-attempts saving the exact value that failed — the only
     * copy of it lives in this component's own local state, so a caller's
     * toast action cannot reconstruct it on its own.
     */
    readonly onError?: (error: unknown, retry: () => void) => void;
    /** The read-only presentation of `value`, shown until the field is clicked into editing. */
    readonly renderValue: (value: T) => ReactNode;
    /**
     * The editing UI for `value` — genuinely generic on purpose: the known
     * call sites (`COMPONENTS.md` §6's interest rating, fit rating, parsed
     * job fields, compensation lines) need a rating widget, a text input, a
     * structured form and a money field respectively, and a component that
     * baked in one of those would need reinventing at the very next call
     * site. `InlineEdit` supplies the click-to-edit shell, the debounce and
     * the keyboard contract; the caller supplies the control.
     */
    readonly renderEditor: (props: InlineEditEditorRenderProps<T>) => ReactNode;
    /**
     * Accessible name for the click-to-edit affordance, rendered alongside
     * — not instead of — whatever `renderValue` shows, via `VisuallyHidden`
     * (`COMPONENTS.md` §12: copy arrives as a prop below Tier 3; this
     * component has no idea what the value it displays even means, so it
     * cannot phrase this itself the way a real call site's own "Edit job
     * title" can).
     */
    readonly editLabel: string;
    /** @default 400, per `ARCHITECTURE.md` §12.9. */
    readonly debounceMs?: number;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

const TRIGGER_CLASS =
    "-mx-inline-tight -my-inline-tight inline-block rounded-control px-inline-tight py-inline-tight text-left outline-none transition-hover hover:bg-surface-row-hover focus-visible:focus-ring";

/** Reuses `Input`'s own `aria-invalid:border-status-danger` signal — a save that failed and has not been retried gets the same visual language a field with an invalid value already carries, rather than a new colour decision. */
const ERROR_CLASS = "border border-status-danger";

/**
 * Tier 1 — the click-to-view/edit toggle behind the product's default write
 * interaction (`COMPONENTS.md`'s own line: "the default write interaction
 * of the product, not a special case"). Composes `useDebouncedAutosave`
 * (`shared/lib`) for the debounce/optimistic/error state machine and
 * `VisuallyHidden` (Tier 0) for the trigger's accessible name; owns only
 * the click-to-edit toggle, the keyboard contract (Enter commits, Escape
 * cancels, losing focus commits), and forwarding failures to the caller.
 *
 * **Why the displayed value survives after a commit, even before `value`
 * itself updates.** `InlineEdit` does not own `value` — the caller does,
 * typically from a real mutation's own cache once one exists (per this
 * batch's plan, decision 3: "a real mutation is wired by whatever Tier 3/4
 * component calls it, not by `InlineEdit` itself"). If this component
 * simply switched back to rendering the incoming `value` prop the instant
 * editing ends, a user pressing Enter would see the *old* value flash back
 * for as long as it takes the caller's own state to catch up — the opposite
 * of "optimistic". `localValue` is this component's own copy, seeded from
 * `value` and re-synced from it only while not editing (so a background
 * update never fights a live edit); a commit sets `localValue` to the
 * just-typed draft and leaves it there, so the display keeps showing what
 * the user typed regardless of how long the save actually takes to confirm.
 */
export function InlineEdit<T>({ value, onSave, onError, renderValue, renderEditor, editLabel, debounceMs, className }: InlineEditProps<T>) {
    const [localValue, setLocalValue] = useState(value);
    const previousValueRef = useRef(value);
    const valueBeforeEditRef = useRef(value);
    const retryRef = useRef<() => void>(() => undefined);

    const { status, retry, flush } = useDebouncedAutosave<T>({
        value: localValue,
        onSave: async (next) => {
            try {
                await onSave(next);
            } catch (error) {
                onError?.(error, () => { retryRef.current(); });
                throw error;
            }
        },
        ...(debounceMs === undefined ? {} : { debounceMs }),
    });
    retryRef.current = retry;

    const session = useEditingSession({
        onCommit: flush,
        onCancel: () => { setLocalValue(valueBeforeEditRef.current); },
    });

    if (!Object.is(previousValueRef.current, value)) {
        previousValueRef.current = value;
        if (!session.isEditing) setLocalValue(value);
    }

    function startEditing() {
        valueBeforeEditRef.current = localValue;
        session.startEditing();
    }

    if (session.isEditing) {
        return (
            <div
                ref={session.containerRef}
                onKeyDown={session.handleKeyDown}
                onBlur={session.handleBlur}
                {...(className ? { className } : {})}
            >
                {renderEditor({ value: localValue, onChange: setLocalValue, commit: session.commit, cancel: session.cancel })}
            </div>
        );
    }

    return (
        <button
            ref={session.triggerRef}
            type="button"
            onClick={startEditing}
            className={cn(TRIGGER_CLASS, status === "error" && ERROR_CLASS, className)}
        >
            {renderValue(localValue)}
            {/* The accessible-name algorithm concatenates adjacent text nodes with no space of its own — without this, "Backend Engineer" and "Edit job title" would read as one run-on word to a screen reader. */}
            {" "}
            <VisuallyHidden>{editLabel}</VisuallyHidden>
        </button>
    );
}
