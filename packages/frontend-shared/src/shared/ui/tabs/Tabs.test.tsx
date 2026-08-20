import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Tabs } from "./Tabs";

function BasicTabs(props: { readonly orientation?: "horizontal" | "vertical" } = {}) {
    return (
        <Tabs.Root orientation={ props.orientation }>
            <Tabs.List>
                <Tabs.Tab value="profile">Profile</Tabs.Tab>
                <Tabs.Tab value="settings">Settings</Tabs.Tab>
                <Tabs.Tab value="billing" disabled>
                    Billing
                </Tabs.Tab>
                <Tabs.Indicator/>
            </Tabs.List>
            <Tabs.Panel value="profile">Profile content.</Tabs.Panel>
            <Tabs.Panel value="settings">Settings content.</Tabs.Panel>
            <Tabs.Panel value="billing">Billing content.</Tabs.Panel>
        </Tabs.Root>
    );
}

describe("Tabs", () => {
    it("exposes a real tablist with three named tabs", () => {
        render(<BasicTabs/>);

        expect(screen.getByRole("tablist")).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "Profile" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "Settings" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "Billing" })).toBeInTheDocument();
    });

    /**
     * `TabsRootProps.defaultValue` defaults to `0`, which never matches a
     * string tab value like `"profile"` — verified empirically rather than
     * assumed to mean "nothing selected": Base UI's own automatic-fallback
     * reasons (`'missing'`, documented on `TabsRoot.Props.onValueChange`)
     * exist for exactly this case, and it does fall back to the first
     * enabled tab in practice.
     */
    it("selects the first enabled tab automatically when no explicit defaultValue matches any tab", () => {
        render(<BasicTabs/>);

        expect(screen.getByRole("tab", { name: "Profile" })).toHaveAttribute("aria-selected", "true");
        expect(screen.getByText("Profile content.")).toBeInTheDocument();
        expect(screen.queryByText("Settings content.")).not.toBeInTheDocument();
    });

    it("selects a tab on click, showing its panel and linking it via aria-controls/aria-labelledby", () => {
        render(<BasicTabs/>);

        fireEvent.click(screen.getByRole("tab", { name: "Settings" }));

        const settingsTab = screen.getByRole("tab", { name: "Settings" });
        expect(settingsTab).toHaveAttribute("aria-selected", "true");
        const panel = screen.getByText("Settings content.");
        expect(panel).toHaveAttribute("role", "tabpanel");
        expect(settingsTab.getAttribute("aria-controls")).toBe(panel.id);
        expect(panel.getAttribute("aria-labelledby")).toBe(settingsTab.id);
    });

    it("stays under caller control when value/onValueChange are set, reporting the next value on click", () => {
        const onValueChange = vi.fn();
        render(
            <Tabs.Root value="profile" onValueChange={ onValueChange }>
                <Tabs.List>
                    <Tabs.Tab value="profile">Profile</Tabs.Tab>
                    <Tabs.Tab value="settings">Settings</Tabs.Tab>
                </Tabs.List>
                <Tabs.Panel value="profile">Profile content.</Tabs.Panel>
                <Tabs.Panel value="settings">Settings content.</Tabs.Panel>
            </Tabs.Root>,
        );

        fireEvent.click(screen.getByRole("tab", { name: "Settings" }));

        expect(onValueChange).toHaveBeenCalledTimes(1);
        expect(onValueChange.mock.calls[0]?.[0]).toBe("settings");
        // Controlled: the DOM does not change on its own since `value` was not fed back in.
        expect(screen.getByRole("tab", { name: "Profile" })).toHaveAttribute("aria-selected", "true");
    });

    it("marks the disabled tab data-disabled and ignores a click on it", () => {
        render(<BasicTabs/>);
        const billingTab = screen.getByRole("tab", { name: "Billing" });

        expect(billingTab).toHaveAttribute("data-disabled");
        fireEvent.click(billingTab);

        expect(billingTab).toHaveAttribute("aria-selected", "false");
    });

    it("gives the active panel a real tabIndex so it is a genuine stop after its tab, not a passive region", () => {
        render(<BasicTabs/>);

        expect(screen.getByText("Profile content.")).toHaveAttribute("tabindex", "0");
    });

    it("carries the focus-visible ring utility on every tab, required of every interactive component in this system", () => {
        render(<BasicTabs/>);
        expect(screen.getByRole("tab", { name: "Profile" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className on List, Tab and Panel with their own classes", () => {
        render(
            <Tabs.Root>
                <Tabs.List className="mt-stack">
                    <Tabs.Tab value="profile" className="mt-stack">
                        Profile
                    </Tabs.Tab>
                </Tabs.List>
                <Tabs.Panel value="profile" className="mt-stack">
                    Profile content.
                </Tabs.Panel>
            </Tabs.Root>,
        );

        expect(screen.getByRole("tablist")).toHaveClass("rounded-control");
        expect(screen.getByRole("tablist")).toHaveClass("mt-stack");
        const tab = screen.getByRole("tab", { name: "Profile" });
        expect(tab).toHaveClass("rounded-control");
        expect(tab).toHaveClass("mt-stack");
        const panel = screen.getByText("Profile content.");
        expect(panel).toHaveClass("rounded-control");
        expect(panel).toHaveClass("mt-stack");
    });

    describe("keyboard navigation", () => {
        /**
         * Base UI moves the roving focus target inside a `queueMicrotask`
         * (verified by reading `useCompositeRoot.js`'s own `onKeyDown`
         * directly: "Wait for FocusManager `returnFocus` to execute"), so
         * every arrow/Home/End assertion below awaits `waitFor` rather than
         * checking `document.activeElement` synchronously right after
         * `fireEvent.keyDown` — the first draft of this suite asserted
         * synchronously and failed even though the navigation was correct,
         * simply because the microtask had not flushed yet.
         */
        it("ArrowRight moves focus to the next tab WITHOUT selecting it (manual activation, the default)", async () => {
            render(<BasicTabs/>);
            const profileTab = screen.getByRole("tab", { name: "Profile" });
            const settingsTab = screen.getByRole("tab", { name: "Settings" });
            profileTab.focus();

            fireEvent.keyDown(profileTab, { key: "ArrowRight" });

            await waitFor(() => expect(document.activeElement).toBe(settingsTab));
            expect(settingsTab).toHaveAttribute("aria-selected", "false");
            expect(profileTab).toHaveAttribute("aria-selected", "true");
        });

        /**
         * Default activation is manual, not automatic — verified against
         * `TabsList.d.ts`'s own `activateOnFocus` doc comment ("@default
         * false... tabs will be activated using Enter or Space key press").
         * A native `<button>`'s own Enter/Space-to-click conversion is the
         * platform's, and jsdom does not implement it (the same limitation
         * `Menu.test.tsx`'s own "keyboard reachability" section documents
         * and works around), so this asserts the actual state change a
         * real Enter press produces — the resulting `click` — rather than
         * a `keyDown` that jsdom would never turn into one.
         */
        it("activating the roving-focused (but not yet selected) tab selects it", () => {
            render(<BasicTabs/>);
            const settingsTab = screen.getByRole("tab", { name: "Settings" });

            fireEvent.click(settingsTab);

            expect(settingsTab).toHaveAttribute("aria-selected", "true");
            expect(screen.getByText("Settings content.")).toBeInTheDocument();
        });

        it("ArrowLeft from the first tab loops to the last enabled tab (loopFocus default)", async () => {
            render(<BasicTabs/>);
            const profileTab = screen.getByRole("tab", { name: "Profile" });
            const billingTab = screen.getByRole("tab", { name: "Billing" });
            profileTab.focus();

            fireEvent.keyDown(profileTab, { key: "ArrowLeft" });

            await waitFor(() => expect(document.activeElement).toBe(billingTab));
        });

        /**
         * Selecting Settings via a real click first, rather than only
         * calling `.focus()` on it directly, is deliberate: Base UI seeds
         * its internal roving-focus index from whichever tab is already
         * selected at mount (`ACTIVE_COMPOSITE_ITEM`), and a bare `.focus()`
         * on a non-default tab does not reliably resynchronize that index
         * on its own — confirmed by a throwaway diagnostic during this
         * batch, not assumed. Clicking first is also the more realistic
         * user path (arrive at a tab, then use arrow keys from it).
         */
        it("a disabled tab still receives roving focus — it is unreachable to activate, not to navigate to", async () => {
            render(<BasicTabs/>);
            const settingsTab = screen.getByRole("tab", { name: "Settings" });
            const billingTab = screen.getByRole("tab", { name: "Billing" });
            fireEvent.click(settingsTab);

            fireEvent.keyDown(settingsTab, { key: "ArrowRight" });

            await waitFor(() => expect(document.activeElement).toBe(billingTab));
            fireEvent.click(billingTab);
            expect(billingTab).toHaveAttribute("aria-selected", "false");
        });

        it("Home and End jump to the first and last tab", async () => {
            render(<BasicTabs/>);
            const profileTab = screen.getByRole("tab", { name: "Profile" });
            const settingsTab = screen.getByRole("tab", { name: "Settings" });
            const billingTab = screen.getByRole("tab", { name: "Billing" });
            settingsTab.focus();

            fireEvent.keyDown(settingsTab, { key: "End" });
            await waitFor(() => expect(document.activeElement).toBe(billingTab));

            fireEvent.keyDown(billingTab, { key: "Home" });
            await waitFor(() => expect(document.activeElement).toBe(profileTab));
        });

        it("switches to ArrowDown/ArrowUp instead of ArrowLeft/ArrowRight when orientation is vertical", async () => {
            render(<BasicTabs orientation="vertical"/>);
            const profileTab = screen.getByRole("tab", { name: "Profile" });
            const settingsTab = screen.getByRole("tab", { name: "Settings" });
            profileTab.focus();

            fireEvent.keyDown(profileTab, { key: "ArrowRight" });
            expect(document.activeElement).toBe(profileTab);

            fireEvent.keyDown(profileTab, { key: "ArrowDown" });
            await waitFor(() => expect(document.activeElement).toBe(settingsTab));
        });
    });
});
