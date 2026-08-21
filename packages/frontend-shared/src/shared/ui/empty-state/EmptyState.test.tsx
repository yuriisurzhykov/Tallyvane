import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "./EmptyState";

describe("EmptyState", () => {
    it("renders the title", () => {
        render(<EmptyState title="No applications yet" />);
        expect(screen.getByText("No applications yet")).toBeInTheDocument();
    });

    it("omits the description when none is given", () => {
        render(<EmptyState title="No applications yet" />);
        expect(screen.queryByText(/track your first/i)).not.toBeInTheDocument();
    });

    it("renders the description when given", () => {
        render(
            <EmptyState
                title="No applications yet"
                description="Track your first application to see it here."
            />,
        );
        expect(screen.getByText("Track your first application to see it here.")).toBeInTheDocument();
    });

    it("renders the icon hidden from assistive technology", () => {
        render(<EmptyState title="No applications yet" icon={<span data-testid="empty-icon" />} />);

        const icon = screen.getByTestId("empty-icon");
        expect(icon).toBeInTheDocument();
        expect(icon.parentElement).toHaveAttribute("aria-hidden", "true");
    });

    it("omits the icon wrapper entirely when no icon is given", () => {
        render(<EmptyState title="No applications yet" />);
        expect(document.querySelector("[aria-hidden='true']")).not.toBeInTheDocument();
    });

    it("renders the caller-provided action", () => {
        render(
            <EmptyState
                title="No applications yet"
                action={<button type="button">Add application</button>}
            />,
        );
        expect(screen.getByRole("button", { name: "Add application" })).toBeInTheDocument();
    });

    it("omits the action entirely when none is given", () => {
        render(<EmptyState title="No applications yet" />);
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it("merges a caller-provided className onto its own layout classes", () => {
        render(<EmptyState title="No applications yet" className="col-span-2" />);
        const container = screen.getByText("No applications yet").parentElement;
        expect(container).toHaveClass("text-center");
        expect(container).toHaveClass("col-span-2");
    });
});
