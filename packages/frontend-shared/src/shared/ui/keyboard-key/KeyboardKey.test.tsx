import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { KeyboardKey } from "./KeyboardKey";

describe("KeyboardKey", () => {
    it("renders its children inside a real kbd element", () => {
        render(<KeyboardKey>Ctrl</KeyboardKey>);

        const element = screen.getByText("Ctrl");
        expect(element.tagName).toBe("KBD");
    });

    it("applies the bordered/inset keycap look from existing surface, border and radius roles", () => {
        render(<KeyboardKey>K</KeyboardKey>);

        const element = screen.getByText("K");
        expect(element).toHaveClass("bg-surface-inset");
        expect(element).toHaveClass("border-border-subtle");
        expect(element).toHaveClass("rounded-chip");
    });

    it("composes with a separator to show a combination, rather than taking a keys array", () => {
        render(
            <span>
                <KeyboardKey>Ctrl</KeyboardKey>+<KeyboardKey>K</KeyboardKey>
            </span>,
        );

        expect(screen.getByText("Ctrl").tagName).toBe("KBD");
        expect(screen.getByText("K").tagName).toBe("KBD");
    });

    it("appends a caller-provided className without dropping its own classes", () => {
        render(<KeyboardKey className="ml-inline-tight">Esc</KeyboardKey>);

        const element = screen.getByText("Esc");
        expect(element).toHaveClass("bg-surface-inset");
        expect(element).toHaveClass("ml-inline-tight");
    });
});
