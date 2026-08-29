import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { AppShell } from "./AppShell";

const NAV_ITEMS = [
    { label: "Today", href: "/today", isActive: true },
    { label: "Pipeline", href: "/pipeline", isActive: false },
];

describe("AppShell", () => {
    it("renders the navigation, the title and the children", () => {
        render(
            <AppShell navItems={NAV_ITEMS} title="Today" skipLinkLabel="Skip to content">
                <p>Screen content</p>
            </AppShell>,
        );

        expect(screen.getByRole("navigation", { name: "Primary" })).toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 1, name: "Today" })).toBeInTheDocument();
        expect(screen.getByText("Screen content")).toBeInTheDocument();
    });

    it("gives the main region the id the skip link points at", () => {
        render(
            <AppShell navItems={NAV_ITEMS} title="Today" skipLinkLabel="Skip to content">
                <p>Screen content</p>
            </AppShell>,
        );

        const skipLink = screen.getByRole("link", { name: "Skip to content" });
        const main = screen.getByRole("main");

        expect(skipLink).toHaveAttribute("href", `#${main.id}`);
    });

    it("places the children inside the main landmark, not beside it", () => {
        render(
            <AppShell navItems={NAV_ITEMS} title="Today" skipLinkLabel="Skip to content">
                <p>Screen content</p>
            </AppShell>,
        );

        expect(screen.getByRole("main")).toContainElement(screen.getByText("Screen content"));
    });

    it("renders TopBar actions when supplied", () => {
        render(
            <AppShell
                navItems={NAV_ITEMS}
                title="Today"
                skipLinkLabel="Skip to content"
                actions={<button type="button">Refresh</button>}
            >
                <p>Screen content</p>
            </AppShell>,
        );

        expect(screen.getByRole("button", { name: "Refresh" })).toBeInTheDocument();
    });
});
