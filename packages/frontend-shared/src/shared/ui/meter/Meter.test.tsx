import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Meter } from "./Meter";

describe("Meter", () => {
    it("exposes role=meter with aria-valuenow/min/max derived from its props", () => {
        render(<Meter label="Salary within range" value={ 72000 } min={ 60000 } max={ 90000 }/>);
        const meter = screen.getByRole("meter");
        expect(meter).toHaveAttribute("aria-valuenow", "72000");
        expect(meter).toHaveAttribute("aria-valuemin", "60000");
        expect(meter).toHaveAttribute("aria-valuemax", "90000");
    });

    it("defaults min to 0 and max to 100, Base UI's own defaults, when omitted", () => {
        render(<Meter label="Value" value={ 40 }/>);
        const meter = screen.getByRole("meter");
        expect(meter).toHaveAttribute("aria-valuemin", "0");
        expect(meter).toHaveAttribute("aria-valuemax", "100");
    });

    it("associates the visible label as the meter's accessible name via aria-labelledby", () => {
        render(<Meter label="Compensation position" value={ 5 } min={ 0 } max={ 10 }/>);
        expect(screen.getByRole("meter", { name: "Compensation position" })).toBeInTheDocument();
    });

    it("renders the default percentage-formatted value text, hidden from the accessibility tree", () => {
        render(<Meter label="Value" value={ 30 } min={ 0 } max={ 100 }/>);
        const value = screen.getByText(/^30\s*%$/);
        expect(value).toHaveAttribute("aria-hidden", "true");
    });

    it("sizes the indicator's width from the value/min/max ratio", () => {
        const { container } = render(<Meter label="Value" value={ 25 } min={ 0 } max={ 100 }/>);
        const indicator = container.querySelector(".bg-interactive-primary") as HTMLElement;
        expect(indicator.style.width).toBe("25%");
    });

    it("has no low/high/optimum sub-range attributes — Base UI's Meter does not implement the native <meter>'s sub-ranges", () => {
        render(<Meter label="Value" value={ 25 } min={ 0 } max={ 100 }/>);
        const meter = screen.getByRole("meter");
        expect(meter).not.toHaveAttribute("low");
        expect(meter).not.toHaveAttribute("high");
        expect(meter).not.toHaveAttribute("optimum");
    });

    it("merges a caller-provided className with its own layout classes", () => {
        const { container } = render(<Meter label="Value" value={ 5 } min={ 0 } max={ 10 } className="mt-stack"/>);
        expect(container.firstElementChild).toHaveClass("mt-stack");
    });
});
