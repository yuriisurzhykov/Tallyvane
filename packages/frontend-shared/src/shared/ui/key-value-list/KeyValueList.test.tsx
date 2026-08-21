import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { KeyValueList } from "./KeyValueList";

describe("KeyValueList", () => {
    it("renders the outer container with no rows for an empty list", () => {
        const { container } = render(<KeyValueList items={[]} />);
        expect(container.firstElementChild).not.toBeNull();
        expect(container.firstElementChild?.children).toHaveLength(0);
    });

    it("renders one row per item, each with its label and value", () => {
        render(
            <KeyValueList
                items={[
                    { label: "Location", value: "Remote" },
                    { label: "Seniority", value: "Senior" },
                ]}
            />,
        );

        expect(screen.getByText("Location")).toBeInTheDocument();
        expect(screen.getByText("Remote")).toBeInTheDocument();
        expect(screen.getByText("Seniority")).toBeInTheDocument();
        expect(screen.getByText("Senior")).toBeInTheDocument();
    });

    it("renders items in the given order", () => {
        render(
            <KeyValueList
                items={[
                    { label: "First", value: "1" },
                    { label: "Second", value: "2" },
                ]}
            />,
        );

        const first = screen.getByText("First");
        const second = screen.getByText("Second");
        expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it("renders a non-text value node as given, without re-styling it", () => {
        render(<KeyValueList items={[{ label: "Status", value: <span data-testid="status-value">Active</span> }]} />);
        expect(screen.getByTestId("status-value")).toHaveTextContent("Active");
    });

    it("places the label and value in the same row, pushed to opposite ends", () => {
        render(
            <KeyValueList
                items={[{ label: "Location", value: <span data-testid="value">Remote</span> }]}
            />,
        );

        const label = screen.getByText("Location");
        const value = screen.getByTestId("value");
        expect(label.parentElement).toBe(value.parentElement);
        expect(label.parentElement).toHaveClass("justify-between");
    });

    it("merges a caller-provided className onto its own layout classes", () => {
        render(<KeyValueList items={[{ label: "Location", value: "Remote" }]} className="col-span-2" />);
        const row = screen.getByText("Location").parentElement;
        const container = row?.parentElement;
        expect(container).toHaveClass("flex-col");
        expect(container).toHaveClass("col-span-2");
    });
});
