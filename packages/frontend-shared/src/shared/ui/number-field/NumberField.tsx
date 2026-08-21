"use client";

import { createContext, type ReactNode, use } from "react";
import { NumberField as BaseNumberField } from "@base-ui/react/number-field";
import { Minus, Plus } from "lucide-react";
import { IconButton } from "../icon-button";
import { Text } from "../text";
import { CONTROL_ICON_CLASS } from "../../lib";

export type NumberFieldSize = "sm" | "md" | "lg";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */
/* ------------------------------------------------------------------------ */

/**
 * A bare re-export, mirroring `Menu.Root`'s and `Popover.Root`'s own
 * reasoning for the identical choice: `Root` renders a plain `<div>` that
 * carries no visual decision of its own — it only owns state (value,
 * min/max/step, scrubbing) that `Group`/`Input`/`Increment`/`Decrement`
 * below read from context. A wrapper that only renamed this prop surface
 * wouldn't earn its place (`SKILL.md` §7).
 */
const NumberFieldRoot = BaseNumberField.Root;
export type NumberFieldRootProps = BaseNumberField.Root.Props;

/* ------------------------------------------------------------------------ */
/* Size — one prop on `Group`, read by `Input`/`Increment`/`Decrement`      */
/* ------------------------------------------------------------------------ */

/**
 * Internal, unexported: propagates the one cosmetic value the three sibling
 * parts need to agree on (their shared height) without repeating a `size`
 * prop on each of them. Not a public compound API in the `state`/`actions`/
 * `meta` sense `SKILL.md` §3.2 describes — there is no behaviour here, only
 * a height role — so a lightweight, unexported context is proportionate
 * where a documented compound contract would be ceremony for one value.
 * Defaults to `"md"` so every part still renders correctly in isolation,
 * without a `Group` ancestor, matching Base UI's own "any part usable on
 * its own" philosophy for this primitive.
 */
const SizeContext = createContext<NumberFieldSize>("md");

const HEIGHT_CLASS: Record<NumberFieldSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

/* ------------------------------------------------------------------------ */
/* Group                                                                     */

/* ------------------------------------------------------------------------ */

export interface NumberFieldGroupOwnProps {
    /** @default "md" */
    readonly size?: NumberFieldSize;
    readonly className?: string;
}

export type NumberFieldGroupProps = NumberFieldGroupOwnProps & Omit<BaseNumberField.Group.Props, "className">;

/**
 * A plain layout row — Base UI's own fixed anatomy nests `Decrement`/
 * `Input`/`Increment` inside `Group` (kept for the same reason `Menu.tsx`
 * keeps `Portal`/`Positioner`/`Popup` nesting even where a part carries no
 * visual decision of its own), but this component gives it no border or
 * fill. See this component's own README for why the bordered "box" look
 * lives on `Input` alone rather than on a single container merging all
 * three parts.
 *
 * `gap-inline`, not `gap-inline-tight` — a real, measured bug: at 4px, the
 * stepper buttons read as pressed flush against the input's own border,
 * confirmed by inspecting the rendered `getBoundingClientRect` gap live
 * rather than assumed from the class name alone. `inline` (8px) is the very
 * next step on the same spacing scale, not an arbitrary value.
 */
function Group({ size = "md", className, ...rest }: NumberFieldGroupProps) {
    return (
        <SizeContext value={ size }>
            <BaseNumberField.Group
                className={ ["inline-flex items-center gap-inline", className].filter(Boolean).join(" ") }
                { ...rest }
            />
        </SizeContext>
    );
}

/* ------------------------------------------------------------------------ */
/* Input                                                                     */

/* ------------------------------------------------------------------------ */

export interface NumberFieldInputOwnProps {
    readonly className?: string;
}

export type NumberFieldInputProps = NumberFieldInputOwnProps & Omit<BaseNumberField.Input.Props, "className">;

/**
 * Ships the same visible box `Input.tsx` ships for the same reason —
 * Tailwind's preflight reset zeroes `background`/`border-width`/`padding`
 * on every `<input>`, Base UI's own field primitives included — reimplemented
 * here rather than imported, since `BaseNumberField.Input` (not this
 * package's `Input`) must remain the literal rendered element: it is the
 * one that carries Base UI's parsing, formatting and clamping logic, which
 * a generic text `Input` does not have. `text-numeric` (this package's own
 * tabular-figures-plus-slashed-zero typography role, `Numeric.tsx`'s own
 * choice for "every salary, count and date") replaces `Input`'s `text-body`
 * here on purpose: a field whose entire job is a number benefits from digits
 * that align and an unambiguous zero while typing, in a way free-form text
 * does not.
 *
 * Invalid styling keys off `data-[invalid]` rather than `aria-invalid:`,
 * the opposite of `Input.tsx`'s own choice, for the opposite reason:
 * `Input.tsx` sits behind this package's `Field.Control`, which guarantees
 * `aria-invalid` reaches the rendered element even with no `Field.Root`
 * ancestor at all. `NumberField.Root` is composed directly under a bare
 * `Field.Root` instead (see this component's own README on why this
 * package's `Field` wrapper doesn't fit a compound control) — Base UI's own
 * `NumberFieldInputDataAttributes` documents `data-invalid` as the attribute
 * it actually sets once wrapped in a real `Field.Root`, so that is the
 * selector this component can rely on.
 */
function Input({ className, ...rest }: NumberFieldInputProps) {
    const size = use(SizeContext);
    return (
        <BaseNumberField.Input
            className={ [
                "min-w-0 flex-1 rounded-control border border-border-default bg-surface-inset px-inline text-numeric text-text-primary transition-hover focus-visible:focus-ring data-[invalid]:border-status-danger data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60",
                HEIGHT_CLASS[size],
                className,
            ]
                .filter(Boolean)
                .join(" ") }
            { ...rest }
        />
    );
}

/* ------------------------------------------------------------------------ */
/* Label                                                                     */

/* ------------------------------------------------------------------------ */

export interface NumberFieldLabelProps {
    readonly htmlFor: string;
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * This package's own `Field` (`Field.Control`'s `render` prop) needs its
 * `children` to be a single element it can clone Base UI's field wiring
 * onto — `NumberField.Root` fails that contract, since the real, literal
 * `<input>` lives several levels down inside `Group`/`Input`, not on `Root`
 * itself. Rather than force this compound control through a slot it cannot
 * fit, it grows its own `Label` — the same shape `Select.Label` already
 * established for the identical reason. A plain native `<label htmlFor>`,
 * not Base UI's own field machinery: unlike `Select`/`Combobox`, this
 * component has no floating popup at all, so there is no `markOthers`
 * concern to design around — `htmlFor`/`id` is simply the correct,
 * unconditional answer here. Composes this package's own `Text`, matching
 * `Select.Label`'s and `Field.tsx`'s own choice (Tier 0 composing Tier 0,
 * `COMPONENTS.md` §2).
 */
function Label({ htmlFor, children, className }: NumberFieldLabelProps) {
    return (
        <Text variant="small" color="primary" className={ className } render={ <label htmlFor={ htmlFor }/> }>
            { children }
        </Text>
    );
}

/* ------------------------------------------------------------------------ */
/* Increment / Decrement                                                     */

/* ------------------------------------------------------------------------ */

export interface NumberFieldStepperOwnProps {
    /** The accessible name — an icon-only stepper button with no name is not a valid button, same rule `IconButton`'s own `label` enforces. */
    readonly label: string;
    /** @default a lucide plus/minus glyph */
    readonly children?: ReactNode;
    readonly className?: string;
}

export type NumberFieldIncrementProps = NumberFieldStepperOwnProps;
export type NumberFieldDecrementProps = NumberFieldStepperOwnProps;

/**
 * Disabled dimming is applied here, not inside `IconButton`: `IconButton`
 * itself carries no `data-[disabled]` treatment of its own (it defers to
 * whatever native `disabled` styling — or none — a caller wants), so the
 * same `data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60` pair
 * `Input.tsx` and `Button.tsx` already use is repeated here to keep a
 * stepper at its `min`/`max` boundary visually, not just functionally,
 * disabled.
 */
const DISABLED_CLASS = "data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/**
 * Composes this package's own `IconButton` via `render` — the exact
 * `<BasePopover.Close render={<IconButton .../>} />` shape `Popover.tsx`'s
 * own `PopoverClose` already established for "a Base UI behavioural part
 * that must render as a real, sized, toned icon button" — rather than a
 * fourth reimplementation of a square icon button. `tone="neutral"` (bordered,
 * no fill) over `"ghost"`: unlike a menu item's leading icon or a dismiss
 * affordance, an increment/decrement control is one of the three behaviours
 * this component's own one-line purpose names explicitly, so it stays
 * discoverable at rest rather than only appearing on hover.
 *
 * `aria-label={label}` is set explicitly on `BaseNumberField.Increment`
 * itself, not only on the composed `IconButton` — verified live, not
 * assumed: Base UI's own `useNumberFieldStepperButton` already sets its own
 * `'aria-label': 'Increase'`/`'Decrease'` into the props merged onto
 * whatever `render` produces, and by the time that merge reaches
 * `IconButton`'s own `aria-label={label}` (computed inside `IconButton`'s
 * body, after `render` has already been evaluated), Base UI's own default
 * has already been cloned into `IconButton`'s incoming props and wins there.
 * Setting `aria-label` at this outer level participates in the *first*
 * merge instead, where an explicit prop already beats Base UI's own
 * internal default — confirmed by `NumberField.test.tsx` asserting the
 * accessible name directly, the same class of bug `toast/README.md`
 * documents for exactly this reason: a plausible-looking composition that a
 * boolean-flag test cannot distinguish from the broken one.
 */
function Increment({ label, size, children, className }: NumberFieldStepperOwnProps & {
    readonly size?: NumberFieldSize
}) {
    const contextSize = use(SizeContext);
    const resolvedSize = size ?? contextSize;
    return (
        <BaseNumberField.Increment
            aria-label={ label }
            render={
                <IconButton label={ label } tone="neutral" size={ resolvedSize }
                            className={ [DISABLED_CLASS, className].filter(Boolean).join(" ") }>
                    { children ?? <Plus className={CONTROL_ICON_CLASS}/> }
                </IconButton>
            }
        />
    );
}

function Decrement({ label, size, children, className }: NumberFieldStepperOwnProps & {
    readonly size?: NumberFieldSize
}) {
    const contextSize = use(SizeContext);
    const resolvedSize = size ?? contextSize;
    return (
        <BaseNumberField.Decrement
            aria-label={ label }
            render={
                <IconButton label={ label } tone="neutral" size={ resolvedSize }
                            className={ [DISABLED_CLASS, className].filter(Boolean).join(" ") }>
                    { children ?? <Minus className={CONTROL_ICON_CLASS}/> }
                </IconButton>
            }
        />
    );
}

/* ------------------------------------------------------------------------ */
/* ScrubArea / ScrubAreaCursor                                               */

/* ------------------------------------------------------------------------ */

export interface NumberFieldScrubAreaOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type NumberFieldScrubAreaProps = NumberFieldScrubAreaOwnProps &
    Pick<BaseNumberField.ScrubArea.Props, "direction" | "pixelSensitivity" | "teleportDistance">;

const DIRECTION_CURSOR_CLASS: Record<"horizontal" | "vertical", string> = {
    horizontal: "cursor-ew-resize",
    vertical: "cursor-ns-resize",
};

/**
 * Wraps whatever the caller supplies — typically their own label, native
 * `<label>` or Base UI's own `Field.Label` — so dragging across that label
 * scrubs the value, exactly Base UI's own documented pattern. This
 * component owns no copy of its own (`COMPONENTS.md` §12: "copy arrives as
 * props below Tier 3"), so it never renders default label text.
 */
function ScrubArea({ children, className, direction = "horizontal", ...rest }: NumberFieldScrubAreaProps) {
    return (
        <BaseNumberField.ScrubArea
            direction={ direction }
            className={ ["select-none", DIRECTION_CURSOR_CLASS[direction], className].filter(Boolean).join(" ") }
            { ...rest }
        >
            { children }
        </BaseNumberField.ScrubArea>
    );
}

export interface NumberFieldScrubAreaCursorProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * The custom pointer glyph shown while scrubbing — deliberately no default
 * content, unlike `Increment`/`Decrement`'s plus/minus: a scrub cursor is a
 * more idiosyncratic visual than a universally-understood plus/minus glyph,
 * so this stays a plain slot rather than a guessed default (YAGNI — no known
 * call site has asked for one yet). `drop-shadow` keeps the glyph legible
 * over any page content it is dragged across, matching Base UI's own
 * reference demo; it is a legibility affordance, not a colour decision, so
 * it needs no token.
 */
function ScrubAreaCursor({ children, className }: NumberFieldScrubAreaCursorProps) {
    return <BaseNumberField.ScrubAreaCursor
        className={ ["drop-shadow-md", className].filter(Boolean).join(" ") }>{ children }</BaseNumberField.ScrubAreaCursor>;
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — a numeric input with increment, decrement and scrub, per
 * `COMPONENTS.md`. Behaviour (parsing, formatting, clamping to `min`/`max`,
 * `step`/`smallStep`/`largeStep` on keyboard/button/wheel interaction, and
 * pointer-drag scrubbing with an optional Pointer-Lock cursor) is entirely
 * Base UI's own `@base-ui/react/number-field` (ADR-031) — verified against
 * its own `.d.ts` and docs rather than assumed: all three named behaviours
 * (increment, decrement, scrub) are real, already-shipped parts
 * (`Increment`, `Decrement`, `ScrubArea`/`ScrubAreaCursor`), not something
 * this component hand-builds. This module supplies only tokens and a
 * smaller compound surface — `Root`/`Group`/`Input`/`Increment`/`Decrement`/
 * `ScrubArea`/`ScrubAreaCursor` — composing this package's own `IconButton`
 * for the two stepper buttons (Tier 0 composing Tier 0, `COMPONENTS.md` §2).
 */
export const NumberField = {
    Root: NumberFieldRoot,
    Label,
    Group,
    Input,
    Increment,
    Decrement,
    ScrubArea,
    ScrubAreaCursor,
};
