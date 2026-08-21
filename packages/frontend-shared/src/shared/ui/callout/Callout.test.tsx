import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Callout, type CalloutTone } from "./Callout";

const TONE_CLASS: Record<CalloutTone, string> = {
    neutral: "bg-surface-inset",
    info: "bg-status-info-subtle",
    attention: "bg-status-attention-subtle",
    success: "bg-status-success-subtle",
    danger: "bg-status-danger-subtle",
};

describe("Callout", () => {
    describe.each(Object.entries(TONE_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`renders with its "${ expectedClass }" wash`, () => {
            render(<Callout tone={ tone as CalloutTone }>An explanation.</Callout>);
            expect(screen.getByRole("note")).toHaveClass(expectedClass);
        });
    });

    it("exposes role=note, since it is persistent supplementary content rather than an alert", () => {
        render(<Callout tone="attention">Budget nearly exhausted.</Callout>);
        expect(screen.getByRole("note")).toBeInTheDocument();
    });

    it("renders its children as the body content, unaffected by the tone text colour applied to the wrapper", () => {
        render(<Callout tone="danger">Filing deadline missed.</Callout>);
        expect(screen.getByText("Filing deadline missed.")).toHaveClass("text-text-primary");
    });

    it("renders no icon wrapper when leadingIcon is omitted", () => {
        const { container } = render(<Callout tone="info">Just text.</Callout>);
        expect(container.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();
    });

    it("renders a leadingIcon, marked decorative, when provided", () => {
        render(
            <Callout tone="info" leadingIcon={ <svg data-testid="icon"/> }>
                With an icon.
            </Callout>,
        );
        const icon = screen.getByTestId("icon");
        expect(icon.parentElement).toHaveAttribute("aria-hidden", "true");
    });

    it("never renders a dismiss affordance — it is always persistent", () => {
        render(<Callout tone="neutral">Persistent note.</Callout>);
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it("merges a caller-provided className with its own classes", () => {
        render(
            <Callout tone="success" className="mt-stack">
                Approved.
            </Callout>,
        );
        const callout = screen.getByRole("note");
        expect(callout).toHaveClass("bg-status-success-subtle");
        expect(callout).toHaveClass("mt-stack");
    });
});
