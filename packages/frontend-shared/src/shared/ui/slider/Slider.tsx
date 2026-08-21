import type { SliderRootProps as BaseSliderRootProps } from "@base-ui/react/slider";
import { Slider as BaseSlider } from "@base-ui/react/slider";

export interface SliderOwnProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * `Value` is fixed to `number`, not passed through as a type parameter the
 * way `Radio`'s is: `COMPONENTS.md`'s own row for this component settles
 * single-thumb only, "no dual-thumb range variant until a real call site
 * needs one" — Base UI's real `SliderRootProps<Value extends number |
 * readonly number[]>` would otherwise let a caller pass an array of values
 * and silently get a multi-thumb slider this component was never designed
 * to style (only one `Slider.Thumb` is rendered below). Locking the
 * generic here makes that a compile error instead of a runtime surprise.
 */
export type SliderProps = SliderOwnProps & Omit<BaseSliderRootProps<number>, "className">;

/**
 * Same reasoning, same literal value, as `Checkbox`'s/`Radio`'s own box
 * size — see `Checkbox.tsx`'s comment on why this is a named constant
 * rather than a token, and why each file keeps its own copy.
 */
const THUMB_SIZE = "1.25rem";

const CONTROL_CLASS_NAME =
    "relative flex w-full touch-none items-center py-inline-tight data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/** `h-inline-tight` (0.25rem) — a thin rail, not a borrowed size the way `Dot`'s `size-inline` borrows one: this is the track's own actual height, and a registered spacing role already happens to be the right number for it. */
const TRACK_CLASS_NAME = "relative w-full grow rounded-pill bg-surface-inset h-inline-tight";

const INDICATOR_CLASS_NAME = "h-full rounded-pill bg-interactive-primary";

/**
 * `focus-within:focus-ring`, not `focus-visible:focus-ring`: the part that
 * actually receives DOM focus is a visually-hidden native
 * `<input type="range">` nested *inside* this outer, visible thumb `<div>`
 * (confirmed by reading `SliderThumb.js` directly, not assumed from the
 * `.d.ts` — Base UI sizes that hidden input to `width: 100%; height: 100%`
 * of the thumb specifically "so that VoiceOver's focus indicator matches
 * the thumb's dimensions"), so `:focus-visible` on this outer element
 * itself would never match. `focus-within` reads the same way
 * `Combobox.tsx`'s own `InputGroup` already handles an identical
 * hidden-focus-target shape, matched here for consistency rather than
 * introducing a second technique (a more surgical `has-[:focus-visible]`
 * was considered and rejected for that reason — see this batch's
 * authoring report).
 */
const THUMB_CLASS_NAME =
    "rounded-pill border-2 border-surface-primary bg-interactive-primary shadow-elevation1 transition-hover focus-within:focus-ring data-[dragging]:cursor-grabbing data-[disabled]:cursor-not-allowed";

/**
 * Tier 0 — a single value dragged or stepped from a numeric range, per
 * `COMPONENTS.md`. Thin styling wrapper over `@base-ui/react/slider`
 * (ADR-031): dragging, keyboard stepping (arrow keys, Home/End, Page Up/
 * Down), value formatting and disabled semantics are entirely Base UI's,
 * which also computes the indicator's fill width and the thumb's own
 * position internally — this component supplies only the rail thickness,
 * the thumb diameter, and tokens for each part's `data-*` state.
 *
 * `aria-label`/`aria-labelledby` are read off the incoming props and
 * re-applied to `Slider.Thumb`, not left on `Slider.Root` — found
 * empirically, not assumed: the actual `role="slider"` element is the
 * native `<input type="range">` nested inside `Thumb` (confirmed by
 * rendering and inspecting the real accessibility tree in
 * `Slider.test.tsx`), while `Root` only ever gets `role="group"`. Base UI's
 * own multi-thumb design expects each thumb's name through `Thumb`'s own
 * `aria-label`/`getAriaLabel` for exactly this reason — a range slider's
 * two thumbs need two different names. A first draft of this component
 * left `aria-label` on `Root` the way every other passthrough wrapper in
 * this batch does, and `Slider.test.tsx`'s first run showed the rendered
 * `role="slider"` element with no accessible name at all — corrected here
 * rather than left for a caller to discover.
 */
export function Slider({
                           className,
                           "aria-label": ariaLabel,
                           "aria-labelledby": ariaLabelledBy,
                           ...props
                       }: SliderProps) {
    return (
        <BaseSlider.Root className={ ["w-full", className].filter(Boolean).join(" ") } { ...props }>
            <BaseSlider.Control className={ CONTROL_CLASS_NAME }>
                <BaseSlider.Track className={ TRACK_CLASS_NAME }>
                    <BaseSlider.Indicator className={ INDICATOR_CLASS_NAME }/>
                    <BaseSlider.Thumb
                        className={ THUMB_CLASS_NAME }
                        style={ { width: THUMB_SIZE, height: THUMB_SIZE } }
                        { ...(ariaLabel ? { "aria-label": ariaLabel } : {}) }
                        { ...(ariaLabelledBy ? { "aria-labelledby": ariaLabelledBy } : {}) }
                    />
                </BaseSlider.Track>
            </BaseSlider.Control>
        </BaseSlider.Root>
    );
}
