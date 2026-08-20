import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { TextArea } from "./TextArea";

describe("TextArea", () => {
    it("renders a textbox", () => {
        render(<TextArea aria-label="Cover letter" />);
        expect(screen.getByRole("textbox")).toBeInTheDocument();
    });

    it("applies the auto-grow CSS property", () => {
        render(<TextArea aria-label="Cover letter" />);
        expect(screen.getByRole("textbox")).toHaveClass("field-sizing-content");
    });

    it("sets a min-height floor for browsers without field-sizing support", () => {
        render(<TextArea aria-label="Cover letter" />);
        expect(screen.getByRole("textbox").style.minHeight).toBe("5.5rem");
    });

    it("keeps native vertical resize as the fallback interaction", () => {
        render(<TextArea aria-label="Cover letter" />);
        expect(screen.getByRole("textbox")).toHaveClass("resize-y");
    });

    it("gets the danger-border treatment when marked aria-invalid", () => {
        render(<TextArea aria-label="Cover letter" aria-invalid="true" />);
        const textarea = screen.getByRole("textbox");
        expect(textarea).toHaveAttribute("aria-invalid", "true");
        expect(textarea).toHaveClass("aria-invalid:border-status-danger");
    });

    it("is visually distinct when disabled", () => {
        render(<TextArea aria-label="Cover letter" disabled />);
        const textarea = screen.getByRole("textbox");
        expect(textarea).toBeDisabled();
        expect(textarea).toHaveClass("disabled:cursor-not-allowed");
        expect(textarea).toHaveClass("disabled:opacity-60");
    });

    it("merges a caller-supplied style with its own min-height default", () => {
        render(<TextArea aria-label="Cover letter" style={{ color: "inherit" }} />);
        const textarea = screen.getByRole("textbox");
        expect(textarea.style.minHeight).toBe("5.5rem");
        expect(textarea.style.color).toBe("inherit");
    });

    it("appends a caller-supplied className", () => {
        render(<TextArea aria-label="Cover letter" className="mt-stack" />);
        expect(screen.getByRole("textbox")).toHaveClass("mt-stack");
        expect(screen.getByRole("textbox")).toHaveClass("bg-surface-inset");
    });
});
