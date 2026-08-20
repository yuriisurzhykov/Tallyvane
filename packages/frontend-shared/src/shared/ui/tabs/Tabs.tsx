import type { ReactNode } from "react";
import { Tabs as BaseTabs } from "@base-ui/react/tabs";

export interface TabsRootOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type TabsRootProps = TabsRootOwnProps & Omit<BaseTabs.Root.Props, "children" | "className">;

/**
 * `orientation` (default `"horizontal"`) is Base UI's own prop, passed
 * straight through rather than reinvented — confirmed against
 * `TabsRoot.d.ts`. Vertical orientation puts the list beside its panel
 * instead of above it (`data-[orientation=vertical]:flex-row`), which is
 * why `Root`, not just `List`, has an orientation-aware class: a vertical
 * tab list next to content stacked underneath it would read as broken
 * layout, not a real vertical-tabs shape.
 */
const ROOT_CLASS = "flex flex-col gap-stack data-[orientation=vertical]:flex-row";

function Root({ children, className, ...rest }: TabsRootProps) {
    return (
        <BaseTabs.Root className={ [ROOT_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseTabs.Root>
    );
}

export interface TabsListOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type TabsListProps = TabsListOwnProps & Omit<BaseTabs.List.Props, "children" | "className">;

/**
 * `relative` is load-bearing, not decorative: `Indicator` positions itself
 * with `left`/`top`/`width`/`height` read from CSS custom properties Base
 * UI computes relative to this element (verified against
 * `TabsIndicator.js`), so without a positioning context here the indicator
 * would place itself relative to the nearest positioned ancestor instead —
 * wherever that happens to be. The border/padding/`rounded-control` track
 * around the whole list is this component's own reading of "visually
 * related to `ToggleGroup`'s look" (this batch's confirmed decision): see
 * this component's README for why the border sits on `List` and the fill
 * sits on `Indicator`, rather than copying `Toggle`'s own per-item border.
 */
const LIST_CLASS =
    "relative inline-flex items-center gap-inline-tight rounded-control border border-border-default p-inline-tight data-[orientation=vertical]:flex-col data-[orientation=vertical]:items-stretch";

function List({ children, className, ...rest }: TabsListProps) {
    return (
        <BaseTabs.List className={ [LIST_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseTabs.List>
    );
}

export interface TabsTabOwnProps {
    /** Optional, not required: a function-form `render` synthesizes its own content and never uses this — see `Accordion.tsx`'s own `Trigger` for a worked example of the same shape. */
    readonly children?: ReactNode;
    readonly className?: string;
}

export type TabsTabProps = TabsTabOwnProps & Omit<BaseTabs.Tab.Props, "children" | "className">;

/**
 * `relative z-10` keeps a tab's own label painted above `Indicator`
 * (`absolute`, `z-0`) regardless of DOM order — two positioned siblings
 * paint by `z-index`, not document order, once either one is explicitly
 * positioned. `data-[active]:text-interactive-primary-text` mirrors
 * `Toggle`'s own `data-[pressed]:text-interactive-primary-text` exactly
 * (`active` is Base UI's own per-tab boolean state, mapped to `data-active`
 * by its default `getStateAttributesProps` behaviour — verified by reading
 * it directly, not assumed) — the filled background that pairs with that
 * text colour lives on `Indicator` instead of repeating it per tab, since
 * there is exactly one active tab at a time and Base UI already computes
 * where to draw one shared, animatable fill.
 */
const TAB_CLASS =
    "relative z-10 inline-flex items-center justify-center gap-inline-tight rounded-control px-stack py-inline text-body-strong text-text-secondary outline-none transition-hover data-[active]:text-interactive-primary-text focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Tab({ children, className, ...rest }: TabsTabProps) {
    return (
        <BaseTabs.Tab className={ [TAB_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseTabs.Tab>
    );
}

export interface TabsIndicatorOwnProps {
    readonly className?: string;
}

export type TabsIndicatorProps = TabsIndicatorOwnProps & Omit<BaseTabs.Indicator.Props, "className">;

/**
 * The filled, sliding backdrop behind whichever tab is active — the
 * confirmed decision's "filled indicator behind the active tab," read
 * literally via Base UI's real `Tabs.Indicator` part (confirmed to exist
 * before reaching for a hand-rolled per-tab background, per this batch's
 * brief) rather than colouring each active `Tab` directly. `bg-interactive-
 * primary-subtle` is the exact fill `Toggle`'s own `data-[pressed]` state
 * already uses for "the one item currently selected in a group," reused
 * here rather than invented a second time.
 *
 * `transition-geometry` animates the slide between adjacent tabs — added
 * once a themed utility covering `left`/`top`/`width`/`height` existed (see
 * `theme/adapters/tailwind.css`'s own comment for why it reuses `popover`'s
 * duration/easing rather than a fourth named pair). `pointer-events-none` is
 * defense in depth on top of the `z-10`/`z-0` stacking above — Base UI
 * already marks this `role="presentation"` (verified against
 * `TabsIndicator.js`), so it should never intercept a click either way.
 */
const INDICATOR_CLASS =
    "absolute z-0 pointer-events-none rounded-control bg-interactive-primary-subtle transition-geometry left-(--active-tab-left) top-(--active-tab-top) h-(--active-tab-height) w-(--active-tab-width)";

function Indicator({ className, ...rest }: TabsIndicatorProps) {
    return <BaseTabs.Indicator className={ [INDICATOR_CLASS, className].filter(Boolean).join(" ") } { ...rest } />;
}

export interface TabsPanelOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type TabsPanelProps = TabsPanelOwnProps & Omit<BaseTabs.Panel.Props, "children" | "className">;

/**
 * No padding or forced typography, the same "the caller's content decides,
 * not this component" reasoning `Collapsible.Panel`'s README explains at
 * more length. `focus-visible:focus-ring` is not decorative here: Base UI
 * gives the active panel a real `tabIndex={0}` (verified against
 * `TabsPanel.js`) so it is a genuine stop after its tab in the tab order,
 * not just a passive content region — `Tabs.test.tsx` asserts this
 * directly rather than assuming the panel is inert.
 */
const PANEL_CLASS = "rounded-control outline-none focus-visible:focus-ring";

function Panel({ children, className, ...rest }: TabsPanelProps) {
    return (
        <BaseTabs.Panel className={ [PANEL_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseTabs.Panel>
    );
}

/**
 * Tier 0 — the `COMPONENTS.md` "Disclosure" row with no Purpose text of its
 * own yet (this batch supplies one; see the authoring report). Behaviour —
 * roving `tabIndex`, `ArrowLeft`/`ArrowRight` (or `ArrowUp`/`ArrowDown` when
 * `orientation="vertical"`) movement between tabs, `Home`/`End`, loop-back
 * at the ends, a disabled tab staying reachable by arrow keys while
 * unreachable to activate (matching `Menu`'s own disabled-item precedent —
 * verified in `Tabs.test.tsx`, not assumed from Menu's behaviour alone),
 * and manual-activation semantics (arrow keys move focus; `Enter`/`Space` —
 * or focus itself, if a caller opts into `activateOnFocus`) — is all Base
 * UI's `@base-ui/react/tabs` (ADR-031), confirmed by reading `TabsList.js`
 * directly rather than assumed: it drives a `CompositeRoot` with
 * `enableHomeAndEndKeys: true` and
 * `loopFocus` defaulting to `true`. This component supplies only tokens
 * and the smaller `Root`/`List`/`Tab`/`Indicator`/`Panel` surface Base UI
 * already ships.
 */
export const Tabs = { Root, List, Tab, Indicator, Panel };
