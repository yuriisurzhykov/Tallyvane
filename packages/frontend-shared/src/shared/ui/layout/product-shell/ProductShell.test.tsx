import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProductShell } from "./ProductShell";

describe("ProductShell", () => {
    it("renders the brand, the content in a main landmark, and a skip link to it", () => {
        render(
            <ProductShell brand={<a href="/">tallyvane</a>} skipLinkLabel="Skip to content">
                <p>Hello</p>
            </ProductShell>,
        );

        expect(screen.getByRole("link", { name: "tallyvane" })).toBeInTheDocument();
        expect(screen.getByRole("main")).toHaveTextContent("Hello");
        expect(screen.getByRole("link", { name: "Skip to content" })).toHaveAttribute("href", "#main-content");
    });

    it("shows the back control, the actions and the dock only when given", () => {
        const { rerender } = render(
            <ProductShell brand="b" skipLinkLabel="s">
                x
            </ProductShell>,
        );
        expect(screen.queryByText("Back")).toBeNull();
        expect(screen.queryByText("Dock")).toBeNull();

        rerender(
            <ProductShell brand="b" back={<span>Back</span>} actions={<span>Acts</span>} dock={<span>Dock</span>} skipLinkLabel="s">
                x
            </ProductShell>,
        );
        expect(screen.getByText("Back")).toBeInTheDocument();
        expect(screen.getByText("Acts")).toBeInTheDocument();
        expect(screen.getByText("Dock")).toBeInTheDocument();
    });

    it("uses the wide column when asked", () => {
        render(
            <ProductShell brand="b" width="wide" skipLinkLabel="s">
                x
            </ProductShell>,
        );

        expect(screen.getByRole("main").className).toContain("--layout-column-wide-max-width");
    });
});
