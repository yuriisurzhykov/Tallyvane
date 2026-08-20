import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge, type BadgeTone } from "./Badge";

const SOLID_CLASS: Record<BadgeTone, string> = {
    neutral: "bg-interactive-primary",
    info: "bg-status-info",
    attention: "bg-status-attention",
    success: "bg-status-success",
    danger: "bg-status-danger",
};

const SUBTLE_CLASS: Record<BadgeTone, string> = {
    neutral: "bg-surface-inset",
    info: "bg-status-info-subtle",
    attention: "bg-status-attention-subtle",
    success: "bg-status-success-subtle",
    danger: "bg-status-danger-subtle",
};

describe("Badge", () => {
    describe.each(Object.entries(SUBTLE_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`defaults to the subtle treatment with its "${ expectedClass }" background`, () => {
            render(<Badge tone={ tone as BadgeTone }>Screening</Badge>);
            expect(screen.getByText("Screening")).toHaveClass(expectedClass);
        });
    });

    describe.each(Object.entries(SOLID_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`renders the solid treatment with its "${ expectedClass }" background`, () => {
            render(
                <Badge tone={ tone as BadgeTone } treatment="solid">
                    Screening
                </Badge>,
            );
            expect(screen.getByText("Screening")).toHaveClass(expectedClass);
        });
    });

    it("pairs the solid treatment with light text so it reads over the fill", () => {
        render(
            <Badge tone="danger" treatment="solid">
                Rejected
            </Badge>,
        );
        expect(screen.getByText("Rejected")).toHaveClass("text-text-on-solid");
    });

    it("reads its shape from the statusBadge component token, not the shared pill/spacing roles", () => {
        render(<Badge tone="info">New</Badge>);
        const badge = screen.getByText("New");
        expect(badge).toHaveClass("rounded-(--ds-component-status-badge-radius)");
        expect(badge).toHaveClass("px-(--ds-component-status-badge-padding-x)");
        expect(badge).toHaveClass("py-(--ds-component-status-badge-padding-y)");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(
            <Badge tone="success" className="ml-inline-tight">
                Hired
            </Badge>,
        );
        const badge = screen.getByText("Hired");
        expect(badge).toHaveClass("bg-status-success-subtle");
        expect(badge).toHaveClass("ml-inline-tight");
    });

    it("renders as a span carrying its label as plain text content", () => {
        render(<Badge tone="neutral">Draft</Badge>);
        const badge = screen.getByText("Draft");
        expect(badge.tagName).toBe("SPAN");
    });
});
