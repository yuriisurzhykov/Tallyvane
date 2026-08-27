import type { ReactNode } from "react";
import { Collapsible as BaseCollapsible } from "@base-ui/react/collapsible";

export type CollapsibleRootProps = BaseCollapsible.Root.Props;

/**
 * `Root` renders a real `<div>` (confirmed against `CollapsibleRoot.d.ts`'s
 * own doc comment: "Renders a `<div>` element" — unlike `Menu.Root`, which
 * renders no DOM at all), but the grouping div needs no visual treatment of
 * its own: `Trigger` then `Panel` simply stack in normal block flow, which an
 * unstyled `<div>` already gives for free. A wrapper that only renames a
 * prop doesn't earn its place (`SKILL.md` §7), so this is re-exported
 * directly rather than wrapped in an empty styling shell.
 */
const Root = BaseCollapsible.Root;

export interface CollapsibleTriggerOwnProps {
    /** Optional, not required: a function-form `render` synthesizes its own content and never uses this — see `Accordion.tsx`'s own `Trigger` for a worked example of the same shape. */
    readonly children?: ReactNode;
    readonly className?: string;
}

export type CollapsibleTriggerProps =
    CollapsibleTriggerOwnProps
    & Omit<BaseCollapsible.Trigger.Props, "children" | "className">;

/**
 * No `justify-between`/`w-full` baked in. `COMPONENTS.md`'s own line for
 * this component — "row expansion is this component inside a table row" —
 * means an icon-only expand caret sitting in a narrow table cell is just as
 * real a call site as a full-width FAQ-style header, and the two want
 * opposite layout defaults. Left content-width and left-aligned, the more
 * neutral of the two; a caller wanting the spread-label-plus-indicator
 * header shape adds `w-full justify-between` itself via `className`.
 */
const TRIGGER_CLASS =
    "inline-flex items-center gap-inline-tight rounded-control px-stack py-inline text-body-strong text-text-primary outline-none transition-hover hover:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Trigger({ children, className, ...rest }: CollapsibleTriggerProps) {
    return (
        <BaseCollapsible.Trigger className={ [TRIGGER_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseCollapsible.Trigger>
    );
}

export interface CollapsiblePanelOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type CollapsiblePanelProps =
    CollapsiblePanelOwnProps
    & Omit<BaseCollapsible.Panel.Props, "children" | "className">;

/**
 * `overflow-hidden` only beyond the height transition below — no padding and
 * no forced typography. Both are the caller's call, for the same reason
 * `Trigger` above leaves layout open: a table row's expanded cell manages
 * its own padding, a FAQ-style answer wants `p-stack` and body copy, and
 * this component knows neither case.
 *
 * `h-(--collapsible-panel-height)` is Base UI's own documented recipe
 * (verified against `useCollapsiblePanel.js`): the variable is a measured
 * pixel value while a transition is in flight (`getAnimationType` requires
 * a non-zero `transition-duration` on this exact element, which is what
 * `transition-geometry` supplies); once idle and open, Base UI sets it to
 * `auto` so the panel sizes to its content again. The 0-height pin cannot
 * be `data-[starting-style]:h-0`: `h-0` is not a class this theme has
 * (`--spacing-*: initial`; same finding `Drawer.tsx`'s `inset: 0` documents),
 * so that pair compiled to nothing and the panel snapped. Numeric `height: 0`
 * while `transitionStatus` is `starting`/`ending` is the geometry-zero that
 * actually reaches the stylesheet.
 */
const PANEL_CLASS = "h-(--collapsible-panel-height) overflow-hidden transition-geometry";

const CLOSED_HEIGHT_STYLE = { height: 0 } as const;

function Panel({ children, className, style, ...rest }: CollapsiblePanelProps) {
    return (
        <BaseCollapsible.Panel
            { ...rest }
            className={ [PANEL_CLASS, className].filter(Boolean).join(" ") }
            style={ (state) => {
                const pin =
                    state.transitionStatus === "starting" || state.transitionStatus === "ending"
                        ? CLOSED_HEIGHT_STYLE
                        : undefined;
                const resolved = typeof style === "function" ? style(state) : style;
                if (!resolved && !pin) return undefined;
                return { ...resolved, ...pin };
            } }
        >
            { children }
        </BaseCollapsible.Panel>
    );
}

/**
 * Tier 0 — the open/close mechanics and tokens for the third depth level
 * (`COMPONENTS.md`'s "Disclosure" row), nothing about what the panel
 * contains. Behaviour — open/close, `data-panel-open`/`data-open`/
 * `data-closed` state, `aria-expanded`/`aria-controls` wiring — is Base
 * UI's `@base-ui/react/collapsible` end to end (ADR-031); the only keyboard
 * path is the trigger being a real `<button>`, so Enter/Space activation is
 * the platform's, not hand-rolled here.
 */
export const Collapsible = { Root, Trigger, Panel };
