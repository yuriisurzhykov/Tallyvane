import { useEffect, useRef, type ChangeEventHandler } from "react";
import { Search, X } from "lucide-react";
import { Input, type InputProps } from "../input";
import { CONTROL_ICON_CLASS, mergeStyle } from "../../lib";

export interface SearchFieldOwnProps {
    readonly value: string;
    /**
     * Same native shape as `Input`'s own `onChange` — deliberately not a
     * `(value: string) => void` shorthand. `Field.Control` (this package's
     * `Field.tsx`) always attaches its own `onChange` to whatever element it
     * renders and composes it with whatever `onChange` that element's own
     * props already carry (`@base-ui/react`'s `mergeProps`, which treats any
     * `on[A-Z]…` key as an event handler to chain, by name alone, regardless
     * of its signature). A value-shaped `onChange` would still get chained
     * the same way, and Field's own composed handler calls
     * `event.currentTarget.value` on whatever it is given — passing it a bare
     * string throws. Keeping the native event shape here is what makes this
     * composition harmless instead of a runtime crash inside `Field`;
     * verified by this component's own `Field` integration test.
     */
    readonly onChange: ChangeEventHandler<HTMLInputElement>;
    /** Fires `debounceMs` after the last change, and once more immediately (bypassing the debounce) when the field is cleared. */
    readonly onSearch: (value: string) => void;
    /** @default 300 */
    readonly debounceMs?: number;
    /** Accessible name for the clear button. Only rendered while `value` is non-empty. */
    readonly clearLabel: string;
}

/**
 * `type` is fixed to `"text"` rather than the semantically closer
 * `"search"`: WebKit renders its own native cancel affordance for
 * `type="search"` once it has a value (`::-webkit-search-cancel-button`),
 * which would sit right next to this component's own clear button — two
 * clear affordances for one field. `role="searchbox"` on the plain text
 * input keeps the intended semantics for assistive tech without that native
 * control. `value`/`onChange`/`defaultValue` are owned by this component's
 * own controlled contract below (no uncontrolled mode — see this batch's
 * authoring report for why).
 */
export type SearchFieldProps = SearchFieldOwnProps & Omit<InputProps, "type" | "value" | "onChange" | "defaultValue">;

/**
 * Glyph plus twice `--spacing-inline` (edge padding and the gap before
 * typed text) — a calc over tokens, not a pre-summed 2rem.
 */
const ICON_INSET = "calc(var(--control-icon) + 2 * var(--spacing-inline))";

/**
 * Tier 0 — text input with a clear affordance and debounce, per
 * `COMPONENTS.md`. Composes `Input` only (unlike `PasswordField`, this one
 * does not reach for `IconButton`: `IconButton` is a square,
 * `control`-height-sized, real button — the right visual weight for a
 * toggle sitting beside a field, but heavier than the minimal, borderless
 * clear glyph a search box's own affordance is everywhere else. Hand-rolled
 * here with the same rigor the volume/frequency threshold in this repo's
 * component-authoring skill calls for at this scale: a real `<button
 * type="button">`, a real accessible name, and a WCAG 2.2 24×24 CSS px hit
 * target (`p-inline-tight` around a 16px glyph) — not a shortcut past the
 * accessibility work.
 *
 * Fully controlled, deliberately with no uncontrolled `defaultValue` mode:
 * every known consumer in `COMPONENTS.md` (`SearchableList`,
 * `filter-pipeline`, `global-search`) already needs the current query in its
 * own state or the caller could not filter or navigate with it, so an
 * uncontrolled mode would only add the controlled/uncontrolled discriminated
 * union's complexity for a call site that does not exist yet (YAGNI).
 */
export function SearchField({
    value,
    onChange,
    onSearch,
    debounceMs = 300,
    clearLabel,
    size = "md",
    className,
    style,
    ...rest
}: SearchFieldProps) {
    const inputRef = useRef<HTMLInputElement>(null);
    const onSearchRef = useRef(onSearch);
    onSearchRef.current = onSearch;
    const pendingTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    /**
     * Depends on `[value, debounceMs]` only, never `onSearch` itself: reading
     * the latest callback through a ref means a caller re-rendering with a
     * fresh inline `onSearch` mid-keystroke does not reset the pending timer
     * the way listing it as a dependency would.
     */
    useEffect(() => {
        pendingTimeoutRef.current = setTimeout(() => { onSearchRef.current(value); }, debounceMs);
        return () => { clearTimeout(pendingTimeoutRef.current); };
    }, [value, debounceMs]);

    function handleClear() {
        const input = inputRef.current;
        if (!input) return;

        // Bypasses whatever pending debounce is in flight — clearing is a
        // direct request to resolve now, not "type nothing and wait".
        clearTimeout(pendingTimeoutRef.current);
        // React tracks the input's `value` through its own patched setter to
        // decide whether a later native event is a "real" change. Setting
        // `input.value` the normal way would update that tracker too, so the
        // `input` event dispatched below would look like a no-op change and
        // never reach `onChange`. Bound directly to the prototype's original
        // setter (never React's patched one), it bypasses the tracker instead.
        const setNativeValue = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set?.bind(input);
        setNativeValue?.("");
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.focus();
        onSearchRef.current("");
    }

    return (
        <div className={["relative", className].filter(Boolean).join(" ")}>
            <Search className={`pointer-events-none absolute top-1/2 left-inline -translate-y-1/2 text-text-muted ${CONTROL_ICON_CLASS}`} />
            <Input
                {...rest}
                ref={inputRef}
                size={size}
                type="text"
                role="searchbox"
                value={value}
                onChange={onChange}
                style={mergeStyle(style, { paddingInlineStart: ICON_INSET, ...(value ? { paddingInlineEnd: ICON_INSET } : {}) })}
            />
            {value ? (
                <button
                    type="button"
                    aria-label={clearLabel}
                    onClick={handleClear}
                    className="absolute top-1/2 right-inline -translate-y-1/2 rounded-control p-inline-tight text-text-muted transition-hover hover:bg-surface-row-hover hover:text-text-primary focus-visible:focus-ring"
                >
                    <X className={CONTROL_ICON_CLASS} />
                </button>
            ) : null}
        </div>
    );
}
