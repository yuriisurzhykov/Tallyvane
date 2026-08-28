import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Text, type TextProps, type TextVariant } from "./Text";

const VARIANT_CLASS: Record<TextVariant, string> = {
    hero: "text-hero",
    display: "text-display",
    title1: "text-title1",
    title2: "text-title2",
    title3: "text-title3",
    body: "text-body",
    bodyStrong: "text-body-strong",
    small: "text-small",
    caption: "text-caption",
    overline: "text-overline",
    numeric: "text-numeric",
};

describe("Text", () => {
    describe.each(Object.entries(VARIANT_CLASS))('variant "%s"', (variant, expectedClass) => {
        it(`renders with the "${expectedClass}" class`, () => {
            render(<Text variant={variant as TextVariant}>Sample text</Text>);
            expect(screen.getByText("Sample text")).toHaveClass(expectedClass);
        });
    });

    it("defaults a heading variant to a span, never a real heading tag (regression guard)", () => {
        render(<Text variant="title1">Card title, not the page heading</Text>);

        const element = screen.getByText("Card title, not the page heading");
        expect(element.tagName).toBe("SPAN");
        expect(element.tagName).not.toMatch(/^H[1-6]$/);
    });

    it("renders a real heading element once the caller opts in via render", () => {
        render(
            <Text variant="title1" render={<h1 />}>
                The one true page heading
            </Text>,
        );

        expect(screen.getByRole("heading", { level: 1, name: "The one true page heading" })).toBeInTheDocument();
    });

    it("defaults body copy to a real paragraph element", () => {
        render(<Text variant="body">Paragraph copy</Text>);
        expect(screen.getByText("Paragraph copy").tagName).toBe("P");
    });

    it("defaults bodyStrong to a real paragraph element too", () => {
        render(<Text variant="bodyStrong">Emphasised paragraph copy</Text>);
        expect(screen.getByText("Emphasised paragraph copy").tagName).toBe("P");
    });

    it("resolves a danger tone to the matching status text colour", () => {
        render(<Text variant="body" tone="danger">Something failed</Text>);
        expect(screen.getByText("Something failed")).toHaveClass("text-status-danger-text");
    });

    it("resolves a secondary color to the matching neutral text colour when no tone is set", () => {
        render(<Text variant="body" color="secondary">Supporting copy</Text>);
        expect(screen.getByText("Supporting copy")).toHaveClass("text-text-secondary");
    });

    it("defaults to the primary text colour when neither tone nor color is set", () => {
        render(<Text variant="body">Default copy</Text>);
        expect(screen.getByText("Default copy")).toHaveClass("text-text-primary");
    });

    it("merges a caller-provided className with its own variant and colour classes", () => {
        render(
            <Text variant="body" className="mt-stack">
                Positioned copy
            </Text>,
        );

        const element = screen.getByText("Positioned copy");
        expect(element).toHaveClass("text-body");
        expect(element).toHaveClass("mt-stack");
    });

    it("does not allow tone and color to be set together (compile-time only)", () => {
        // @ts-expect-error — tone and color are mutually exclusive per the discriminated union: a
        // status tone already implies its own text colour.
        const invalidProps: TextProps = { variant: "body", tone: "danger", color: "secondary" };
        expect(invalidProps).toBeDefined();
    });
});
