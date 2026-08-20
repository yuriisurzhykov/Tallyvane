import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { AspectRatio } from "./AspectRatio";

describe("AspectRatio", () => {
    it("applies the given ratio as the CSS aspect-ratio property", () => {
        render(
            <AspectRatio ratio={16 / 9}>
                <p>Media</p>
            </AspectRatio>,
        );

        const box = screen.getByText("Media").parentElement;
        expect(box).toHaveStyle({ aspectRatio: String(16 / 9) });
    });

    it("applies a different ratio", () => {
        render(
            <AspectRatio ratio={1}>
                <p>Media</p>
            </AspectRatio>,
        );

        const box = screen.getByText("Media").parentElement;
        expect(box).toHaveStyle({ aspectRatio: "1" });
    });

    it("renders children", () => {
        render(
            <AspectRatio ratio={4 / 3}>
                <p>Media</p>
            </AspectRatio>,
        );

        expect(screen.getByText("Media")).toBeInTheDocument();
    });

    it("appends a caller-supplied className", () => {
        render(
            <AspectRatio ratio={1} className="rounded-card">
                <p>Media</p>
            </AspectRatio>,
        );

        expect(screen.getByText("Media").parentElement).toHaveClass("rounded-card");
    });
});
