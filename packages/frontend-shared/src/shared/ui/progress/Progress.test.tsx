import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Progress } from "./Progress";

describe("Progress", () => {
    it("exposes role=progressbar with aria-valuenow/min/max derived from value and max", () => {
        render(<Progress label="Weekly goal" value={ 12 } max={ 20 }/>);
        const bar = screen.getByRole("progressbar");
        expect(bar).toHaveAttribute("aria-valuenow", "12");
        expect(bar).toHaveAttribute("aria-valuemin", "0");
        expect(bar).toHaveAttribute("aria-valuemax", "20");
    });

    it("defaults max to 100, Base UI's own default, when omitted", () => {
        render(<Progress label="Weekly goal" value={ 30 }/>);
        expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuemax", "100");
    });

    it("associates the visible label as the progress bar's accessible name via aria-labelledby", () => {
        render(<Progress label="Weekly application goal" value={ 5 } max={ 10 }/>);
        expect(screen.getByRole("progressbar", { name: "Weekly application goal" })).toBeInTheDocument();
    });

    it("renders the label text visibly", () => {
        render(<Progress label="Weekly application goal" value={ 5 } max={ 10 }/>);
        expect(screen.getByText("Weekly application goal")).toBeInTheDocument();
    });

    it("renders the default percentage-formatted value text, hidden from the accessibility tree since aria-valuenow already carries it", () => {
        // `Intl.NumberFormat`'s percent style inserts a narrow no-break space
        // before "%" in this runtime's ICU data ("30\u202f%"), not a plain
        // ASCII space — matched loosely rather than pinned to one exact
        // whitespace character.
        render(<Progress label="Weekly goal" value={ 30 } max={ 100 }/>);
        const value = screen.getByText(/^30\s*%$/);
        expect(value).toHaveAttribute("aria-hidden", "true");
    });

    it("sizes the indicator's width from the value/max ratio", () => {
        const { container } = render(<Progress label="Weekly goal" value={ 30 } max={ 100 }/>);
        const indicator = container.querySelector<HTMLElement>(".bg-interactive-primary");
        expect(indicator?.style.width).toBe("30%");
    });

    it("reports complete once value reaches max", () => {
        render(<Progress label="Weekly goal" value={ 20 } max={ 20 }/>);
        expect(screen.getByRole("progressbar")).toHaveAttribute("data-complete", "");
    });

    it("merges a caller-provided className with its own layout classes", () => {
        const { container } = render(<Progress label="Weekly goal" value={ 5 } max={ 10 } className="mt-stack"/>);
        expect(container.firstElementChild).toHaveClass("mt-stack");
    });
});
