import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { PageFrame } from "./PageFrame";

describe("PageFrame", () => {
    it("renders the side column, the heading and the children", () => {
        render(
            <PageFrame sidebar={<p>Side</p>} title="Sign-in policies" skipLinkLabel="Skip to content">
                <p>Screen content</p>
            </PageFrame>,
        );

        expect(screen.getByText("Side")).toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 1, name: "Sign-in policies" })).toBeInTheDocument();
        expect(screen.getByText("Screen content")).toBeInTheDocument();
    });

    it("puts the side column in a landmark of its own and the content in the main one", () => {
        render(
            <PageFrame sidebar={<p>Side</p>} title="Title" skipLinkLabel="Skip to content">
                <p>Screen content</p>
            </PageFrame>,
        );

        expect(screen.getByRole("complementary")).toContainElement(screen.getByText("Side"));
        expect(screen.getByRole("main")).toContainElement(screen.getByText("Screen content"));
        expect(screen.getByRole("main")).not.toContainElement(screen.getByText("Side"));
    });

    it("gives the main region the id the skip link points at", () => {
        render(
            <PageFrame sidebar={null} title="Title" skipLinkLabel="Skip to content">
                <p>Screen content</p>
            </PageFrame>,
        );

        expect(screen.getByRole("link", { name: "Skip to content" })).toHaveAttribute("href", `#${screen.getByRole("main").id}`);
    });

    it("shows the lead and the actions only when given", () => {
        const { rerender } = render(
            <PageFrame sidebar={null} title="Title" skipLinkLabel="Skip">
                <p>Body</p>
            </PageFrame>,
        );
        expect(screen.queryByText("A line of explanation")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "New version" })).not.toBeInTheDocument();

        rerender(
            <PageFrame sidebar={null} title="Title" lead="A line of explanation" actions={<button type="button">New version</button>} skipLinkLabel="Skip">
                <p>Body</p>
            </PageFrame>,
        );
        expect(screen.getByText("A line of explanation")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "New version" })).toBeInTheDocument();
    });
});
