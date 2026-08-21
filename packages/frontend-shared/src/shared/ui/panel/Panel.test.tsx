import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Panel } from "./Panel";

describe("Panel", () => {
    it("defaults to the primary Surface variant's background class", () => {
        render(<Panel>Body</Panel>);

        expect(screen.getByText("Body").parentElement).toHaveClass("bg-surface-primary");
    });

    it("proxies each Surface variant through to the underlying Surface", () => {
        render(<Panel variant="elevated">Body</Panel>);

        expect(screen.getByText("Body").parentElement).toHaveClass("bg-surface-elevated");
    });

    it("renders only the body when header and footer are omitted, with no separator", () => {
        render(<Panel>Body only</Panel>);

        expect(screen.getByText("Body only")).toBeInTheDocument();
        expect(screen.queryByRole("separator")).not.toBeInTheDocument();
    });

    it("renders a header above the body, divided by a separator", () => {
        render(<Panel header="Title">Body</Panel>);

        const header = screen.getByText("Title");
        const body = screen.getByText("Body");
        const separator = screen.getByRole("separator");

        expect(header.compareDocumentPosition(separator) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(separator.compareDocumentPosition(body) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it("renders a footer below the body, divided by a separator", () => {
        render(<Panel footer="Actions">Body</Panel>);

        const body = screen.getByText("Body");
        const separator = screen.getByRole("separator");
        const footer = screen.getByText("Actions");

        expect(body.compareDocumentPosition(separator) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(separator.compareDocumentPosition(footer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it("renders header, body and footer with two separators when all three are present", () => {
        render(
            <Panel header="Title" footer="Actions">
                Body
            </Panel>,
        );

        expect(screen.getByText("Title")).toBeInTheDocument();
        expect(screen.getByText("Body")).toBeInTheDocument();
        expect(screen.getByText("Actions")).toBeInTheDocument();
        expect(screen.getAllByRole("separator")).toHaveLength(2);
    });

    it("appends a caller-provided className to Surface's own classes", () => {
        render(
            <Panel className="col-span-2" header="Title">
                Body
            </Panel>,
        );

        const surface = screen.getByText("Title").parentElement;
        expect(surface).toHaveClass("bg-surface-primary");
        expect(surface).toHaveClass("col-span-2");
    });
});
