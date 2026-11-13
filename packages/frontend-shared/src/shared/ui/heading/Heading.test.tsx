import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Heading } from "./Heading";

describe("Heading", () => {
    it.each([1, 2, 3, 4] as const)("renders level %i as a real heading of that level", (level) => {
        render(<Heading level={level} variant="title1">Title</Heading>);

        expect(screen.getByRole("heading", { level, name: "Title" })).toBeInTheDocument();
    });

    it("takes its look from the variant, not from the level", () => {
        render(<Heading level={2} variant="small">Small</Heading>);

        expect(screen.getByRole("heading", { level: 2 })).toHaveClass("text-small");
    });
});
