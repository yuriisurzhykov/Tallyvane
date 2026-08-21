import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Stat } from "./Stat";

describe("Stat", () => {
    it("renders the label", () => {
        render(<Stat label="Applications this week" value="12" />);
        expect(screen.getByText("Applications this week")).toBeInTheDocument();
    });

    it("renders the value node as given, without reformatting it", () => {
        render(<Stat label="Applications this week" value={<span data-testid="stat-value">12</span>} />);
        expect(screen.getByTestId("stat-value")).toHaveTextContent("12");
    });

    it("omits the delta entirely when none is given", () => {
        render(<Stat label="Applications this week" value="12" />);
        expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    });

    it("colours a success delta via Text's own success tone", () => {
        render(<Stat label="Applications this week" value="12" delta={{ value: "+12%", tone: "success" }} />);
        expect(screen.getByText("+12%")).toHaveClass("text-status-success-text");
    });

    it("colours a danger delta via Text's own danger tone", () => {
        render(<Stat label="Applications this week" value="12" delta={{ value: "-4%", tone: "danger" }} />);
        expect(screen.getByText("-4%")).toHaveClass("text-status-danger-text");
    });

    it("colours a neutral delta with the secondary text role", () => {
        render(<Stat label="Applications this week" value="12" delta={{ value: "No change", tone: "neutral" }} />);
        expect(screen.getByText("No change")).toHaveClass("text-text-secondary");
    });

    it("merges a caller-provided className onto its own layout classes", () => {
        render(<Stat label="Applications this week" value="12" className="col-span-2" />);
        const container = screen.getByText("Applications this week").parentElement;
        expect(container).toHaveClass("col-span-2");
    });
});
