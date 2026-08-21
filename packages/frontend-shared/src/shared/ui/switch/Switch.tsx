import type { CSSProperties } from "react";
import type { SwitchRootProps as BaseSwitchRootProps } from "@base-ui/react/switch";
import { Switch as BaseSwitch } from "@base-ui/react/switch";

export interface SwitchOwnProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * `checked`/`defaultChecked`/`onCheckedChange` are Base UI's own
 * controlled/uncontrolled trio, verified against
 * `@base-ui/react/switch`'s `SwitchRoot.d.ts` — no second state mechanism
 * added here, same as `Toggle`/`Checkbox`.
 */
export type SwitchProps = SwitchOwnProps & Omit<BaseSwitchRootProps, "className">;

/**
 * Genuinely tokenless geometry — no spacing role names "how big a switch
 * track is" any more than one names "how wide a drawer is"
 * (`Drawer.tsx`'s own `DRAWER_WIDTH`). Chosen so the numbers work out to
 * clean values with the registered spacing roles used for the rest of the
 * track below: track height minus twice the inset padding (`p-inline-tight`,
 * 0.25rem) leaves exactly `THUMB_SIZE`, and track width minus the thumb and
 * its insets leaves exactly `THUMB_TRAVEL` — no fractional pixel anywhere.
 */
const TRACK_WIDTH = "2.5rem";
const TRACK_HEIGHT = "1.5rem";
/** Coincides with `--spacing-stack` (`{dimension.4}`, 1rem) but kept as its own named constant rather than the `size-stack` class: this is the switch's own geometry, not a borrowed layout gap, and a reviewer should not have to know the coincidence to read this file. */
const THUMB_SIZE = "1rem";
/** `TRACK_WIDTH − THUMB_SIZE − 2 × the track's own `p-inline-tight` inset = 1rem exactly. */
const THUMB_TRAVEL = "1rem";

const TRACK_CLASS_NAME =
    "inline-flex shrink-0 items-center rounded-pill border border-border-default bg-surface-inset p-inline-tight transition-hover focus-visible:focus-ring data-[checked]:border-interactive-primary data-[checked]:bg-interactive-primary aria-invalid:border-status-danger data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/**
 * `transition-transform` is Tailwind's own bare, tokenless utility (not a
 * themed `transition-*` role like `transition-hover`): it only turns on
 * `transition-property: transform` at Tailwind's stock default duration
 * and easing, since neither is cleared by the adapter the way `--ease-*`/
 * `--animate-*` are. A themed alternative would need a new
 * `transition-*` utility in `theme/adapters/tailwind.css`, out of scope
 * for a single component — flagged in this batch's authoring report. The
 * global `prefers-reduced-motion` rule in that same file still applies
 * regardless, since it targets `*` unconditionally.
 *
 * `bg-text-on-solid`, not `bg-surface-primary` — a real, measured contrast
 * bug found in a live render, not caught by any test: `surface-primary`
 * (`neutral.1000`, the *darkest* neutral) sat on `surface-inset`'s own
 * track (`neutral.900`), so the thumb was visually darker than the track
 * it floats on, with only `shadow-elevation1` — already documented
 * elsewhere in this theme as barely reading on a dark ground — to
 * distinguish it at all. A switch thumb is a physical knob, not a layer of
 * page surface, and needs a fixed, theme-invariant brightness regardless of
 * which surface it sits on. `textOnSolid` (`sharedColorRoles`, always
 * `neutral.0` in both themes) is reused for exactly its literal meaning —
 * "white, now that the fills are deep enough to carry it" applies here too,
 * even though this fill's neighbour is a track, not a status colour.
 */
const THUMB_CLASS_NAME = "rounded-pill bg-text-on-solid shadow-elevation1 transition-transform data-[checked]:translate-x-(--switch-thumb-travel)";

/**
 * Tier 0 — thin styling wrapper over Base UI's `Switch` (ADR-031): checked
 * state, keyboard activation (Space, via the same non-native-button
 * mechanism `Checkbox`/`Radio` use) and disabled semantics all come from
 * `@base-ui/react/switch`, which emits `data-checked`/`data-disabled`
 * rather than classes — this component only maps those onto tokens.
 *
 * The thumb slides via `transform: translateX(...)`, not a `justify-start`/
 * `justify-end` flip: `justify-content` is a discrete CSS property with no
 * defined interpolation, so a browser snaps it at the transition's
 * halfway point instead of animating smoothly — the sliding motion a
 * switch is expected to have would be lost. The travel distance is a CSS
 * custom property set inline and read back through Tailwind's
 * `translate-x-(--switch-thumb-travel)` paren syntax, the same mechanism
 * `Input`/`Button`/`IconButton` already use for `h-(--control-height-*)`,
 * so no bare pixel value ever appears in a class name.
 */
export function Switch({ className, style, ...props }: SwitchProps) {
    return (
        <BaseSwitch.Root
            className={ [TRACK_CLASS_NAME, className].filter(Boolean).join(" ") }
            style={ { ...style, width: TRACK_WIDTH, height: TRACK_HEIGHT } }
            { ...props }
        >
            <BaseSwitch.Thumb
                className={ THUMB_CLASS_NAME }
                style={ {
                    width: THUMB_SIZE,
                    height: THUMB_SIZE,
                    "--switch-thumb-travel": THUMB_TRAVEL
                } as CSSProperties }
            />
        </BaseSwitch.Root>
    );
}
