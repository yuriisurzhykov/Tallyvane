import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Money } from "./Money";

/**
 * Computed with the exact call `Money.tsx` itself makes, not a hardcoded
 * `"$42.50"` — this machine's own default `Intl` locale is `ru-UA`
 * (verified directly, same as `money-field/README.md`'s own finding),
 * under which USD currency formats as `"42,50\u00A0$"`. Asserting against
 * this computed value proves `Money` performs the documented cents→display
 * conversion, without the test itself becoming locale-fragile.
 *
 * The `\u00A0`→space replacement is not cosmetic: `Intl.NumberFormat`
 * separates the amount from the currency symbol with a real non-breaking
 * space under this locale, and `@testing-library/dom`'s default text
 * normalizer collapses that (along with any other whitespace) on the
 * *rendered* node before comparing, but does not normalize a plain string
 * matcher the same way — so comparing the raw `Intl.NumberFormat` output
 * against the normalized DOM text fails on a byte the terminal renders
 * identically either way. Normalizing here matches what the DOM-side
 * comparison already does, rather than turning off normalization on both
 * sides.
 */
function expectedFormat(cents: number, currency = "USD"): string {
    return new Intl.NumberFormat(undefined, { style: "currency", currency })
        .format(cents / 100)
        .replace(/\s/g, " ");
}

describe("Money", () => {
    it("formats integer cents as USD currency by default", () => {
        render(<Money cents={4250} />);
        expect(screen.getByText(expectedFormat(4250))).toBeInTheDocument();
    });

    it("formats a value spanning a thousands separator", () => {
        render(<Money cents={425000} />);
        expect(screen.getByText(expectedFormat(425000))).toBeInTheDocument();
    });

    it("formats zero cents", () => {
        render(<Money cents={0} />);
        expect(screen.getByText(expectedFormat(0))).toBeInTheDocument();
    });

    it("formats negative cents", () => {
        render(<Money cents={-500} />);
        expect(screen.getByText(expectedFormat(-500))).toBeInTheDocument();
    });

    it("supports a non-USD currency via the currency prop", () => {
        render(<Money cents={999} currency="EUR" />);
        expect(screen.getByText(expectedFormat(999, "EUR"))).toBeInTheDocument();
    });

    it("renders through Numeric's tabular-figure, right-aligned typography", () => {
        render(<Money cents={4250} />);
        const element = screen.getByText(expectedFormat(4250));
        expect(element).toHaveClass("text-numeric");
        expect(element).toHaveClass("text-right");
    });

    it("merges a caller-provided className onto Numeric's own classes", () => {
        render(<Money cents={4250} className="col-span-2" />);
        const element = screen.getByText(expectedFormat(4250));
        expect(element).toHaveClass("text-numeric");
        expect(element).toHaveClass("col-span-2");
    });
});
