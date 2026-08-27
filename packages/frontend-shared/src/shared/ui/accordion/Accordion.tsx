import type { ReactNode } from "react";
import { Accordion as BaseAccordion } from "@base-ui/react/accordion";

export type AccordionRootProps<Value = unknown> = { readonly children: ReactNode; readonly className?: string } & Omit<
    BaseAccordion.Root.Props<Value>,
    "children" | "className"
>;

/**
 * Renders a real `<div>` (confirmed against `AccordionRoot.d.ts`'s own doc
 * comment), but the grouping div needs no visual treatment of its own —
 * `Item`s already stack correctly in plain block flow, exactly the same
 * reasoning `Collapsible.Root` already uses one directory over. Re-exported
 * directly rather than wrapped in an empty styling shell (`SKILL.md` §7);
 * the generic `Value` type parameter survives a plain assignment the same
 * way `Menu.tsx`'s own `MenuRoot = BaseMenu.Root` already relies on.
 */
const Root = BaseAccordion.Root;

export interface AccordionItemOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type AccordionItemProps = AccordionItemOwnProps & Omit<BaseAccordion.Item.Props, "children" | "className">;

/**
 * `last:border-b-0` rather than a gap token between items: a continuous
 * bordered list (each row divided from the next, no border below the last
 * row) is the standard FAQ-block shape, and it needs no group-level
 * container of its own on `Root` — the dividers live on the rows, the same
 * way a plain HTML list needs no wrapping box to look like a list.
 */
const ITEM_CLASS = "border-b border-border-subtle last:border-b-0";

function Item({ children, className, ...rest }: AccordionItemProps) {
    return (
        <BaseAccordion.Item className={ [ITEM_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseAccordion.Item>
    );
}

export interface AccordionHeaderOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type AccordionHeaderProps = AccordionHeaderOwnProps & Omit<BaseAccordion.Header.Props, "children" | "className">;

/**
 * Numeric `0`, not `m-0`: that class compiles to nothing under
 * `--spacing-*: initial` (same finding `Drawer.tsx`'s `inset: 0` documents).
 */
const HEADER_STYLE = { margin: 0 } as const;

function Header({ children, className, style, ...rest }: AccordionHeaderProps) {
    return (
        <BaseAccordion.Header
            { ...rest }
            className={ className }
            style={
                typeof style === "function"
                    ? (state) => ({ ...style(state), ...HEADER_STYLE })
                    : { ...style, ...HEADER_STYLE }
            }
        >
            { children }
        </BaseAccordion.Header>
    );
}

export interface AccordionTriggerOwnProps {
    /** Optional, not required: a function-form `render` (see below) synthesizes its own content and never uses this. */
    readonly children?: ReactNode;
    readonly className?: string;
}

export type AccordionTriggerProps =
    AccordionTriggerOwnProps
    & Omit<BaseAccordion.Trigger.Props, "children" | "className">;

/**
 * `w-full justify-between`, unlike `Collapsible.Trigger`'s own
 * content-width default: `COMPONENTS.md`'s row for this component ("the
 * FAQ block, and documentation sections") names one specific shape, not
 * `Collapsible`'s two competing ones (a table row's expand caret versus a
 * full header), so there is a single reasonable default to bake in here.
 * A caller wanting a rotating indicator that tracks open state uses the
 * function-form `render` prop (`(props, state) => ...`, `state.open` is
 * already exposed by Base UI) rather than a CSS attribute selector — see
 * this component's README for why, and `Accordion.stories.tsx`'s
 * `WithIndicator` story for a worked example.
 */
const TRIGGER_CLASS =
    "flex w-full items-center justify-between gap-inline-tight rounded-control px-stack py-inline text-left text-body-strong text-text-primary outline-none transition-hover hover:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Trigger({ children, className, ...rest }: AccordionTriggerProps) {
    return (
        <BaseAccordion.Trigger className={ [TRIGGER_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseAccordion.Trigger>
    );
}

export interface AccordionPanelOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type AccordionPanelProps = AccordionPanelOwnProps & Omit<BaseAccordion.Panel.Props, "children" | "className">;

/**
 * No padding on this element. Collapsible.Panel's own comment is the reason:
 * height animates on the panel, and padding here would keep a non-zero box
 * when height is pinned to 0. Horizontal rhythm with `Trigger`'s `px-stack`
 * is the caller's (`px-stack pb-stack` on the inner content), matching what
 * `Collapsible.stories` already does with `p-stack`.
 *
 * `h-(--accordion-panel-height)` plus `transition-geometry` is Base UI's
 * recipe (`AccordionPanelCssVars`: a distinct name from Collapsible's, even
 * though both panels share the identical measurement mechanism). The 0-height
 * pin cannot be `data-[starting-style]:h-0`: `h-0` is not a class this theme
 * has, so that pair compiled to nothing and Base UI saw a duration with no
 * height change — a snap. Numeric `height: 0` while `transitionStatus` is
 * `starting`/`ending` is the same geometry-zero `Drawer.tsx` already uses.
 */
const PANEL_CLASS = "h-(--accordion-panel-height) overflow-hidden transition-geometry";

const CLOSED_HEIGHT_STYLE = { height: 0 } as const;

function Panel({ children, className, style, ...rest }: AccordionPanelProps) {
    return (
        <BaseAccordion.Panel
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
        </BaseAccordion.Panel>
    );
}

/**
 * Tier 0 — the FAQ block and documentation-section disclosure group
 * (`COMPONENTS.md`'s "Disclosure" row), nothing about what any answer or
 * section actually contains. `multiple` (default `false`, single-open) is
 * passed straight through from `@base-ui/react/accordion` rather than
 * reinvented — confirmed against `AccordionRoot.js` directly: with
 * `multiple` unset, opening one item always replaces the whole open-value
 * array (`nextValue = [newValue]`), which is the real single-open FAQ
 * behaviour, not an approximation of it. Roving arrow-key focus between
 * triggers is deliberately absent, not a gap: verified against
 * `AccordionTrigger.js`, which attaches no keydown handler of its own,
 * matching Base UI's documented removal of roving focus from this
 * component following the ARIA APG's own guidance update (see
 * `AccordionRootState.orientation`'s `@deprecated` doc comment) — every
 * trigger is simply a normal tab stop, and this component does not
 * reintroduce the retired behaviour by hand.
 */
export const Accordion = { Root, Item, Header, Trigger, Panel };
