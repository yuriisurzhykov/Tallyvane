import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SidebarNav } from "./SidebarNav";

const ITEMS = [
    { label: "Today", href: "/today", isActive: true },
    { label: "Pipeline", href: "/pipeline", isActive: false },
    { label: "Contacts", href: "/contacts", isActive: false },
];

describe("SidebarNav", () => {
    it("renders one link per item, in order", () => {
        render(<SidebarNav items={ITEMS} />);

        const links = screen.getAllByRole("link");
        expect(links).toHaveLength(3);
        expect(links.map((link) => link.textContent)).toEqual(["Today", "Pipeline", "Contacts"]);
    });

    it("points each link at its own href", () => {
        render(<SidebarNav items={ITEMS} />);

        expect(screen.getByRole("link", { name: "Pipeline" })).toHaveAttribute("href", "/pipeline");
    });

    it("marks the active item with aria-current, and no other item", () => {
        render(<SidebarNav items={ITEMS} />);

        expect(screen.getByRole("link", { name: "Today" })).toHaveAttribute("aria-current", "page");
        expect(screen.getByRole("link", { name: "Pipeline" })).not.toHaveAttribute("aria-current");
        expect(screen.getByRole("link", { name: "Contacts" })).not.toHaveAttribute("aria-current");
    });

    it("is a labelled navigation landmark", () => {
        render(<SidebarNav items={ITEMS} />);

        expect(screen.getByRole("navigation", { name: "Primary" })).toBeInTheDocument();
    });

    it("appends a caller-provided className to its own classes", () => {
        render(<SidebarNav items={ITEMS} className="border-r" />);

        expect(screen.getByRole("navigation")).toHaveClass("border-r");
    });

    it("renders nothing but the landmark when there are no items", () => {
        render(<SidebarNav items={[]} />);

        expect(screen.getByRole("navigation")).toBeEmptyDOMElement();
    });
});
