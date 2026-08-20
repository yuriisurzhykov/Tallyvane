import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { PasswordField } from "./PasswordField";
import { Field } from "../field";

const TOGGLE_LABELS = { showPasswordLabel: "Show password", hidePasswordLabel: "Hide password" };

describe("PasswordField", () => {
    it("renders as a masked password input by default", () => {
        render(<PasswordField aria-label="Password" {...TOGGLE_LABELS} />);
        expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
    });

    it("reveals the value and swaps the toggle's accessible name and pressed state when clicked", () => {
        render(<PasswordField aria-label="Password" {...TOGGLE_LABELS} />);

        const toggle = screen.getByRole("button", { name: "Show password" });
        expect(toggle).toHaveAttribute("aria-pressed", "false");

        fireEvent.click(toggle);

        expect(screen.getByLabelText("Password")).toHaveAttribute("type", "text");
        const hideToggle = screen.getByRole("button", { name: "Hide password" });
        expect(hideToggle).toHaveAttribute("aria-pressed", "true");
    });

    it("masks the value again on a second click", () => {
        render(<PasswordField aria-label="Password" {...TOGGLE_LABELS} />);

        fireEvent.click(screen.getByRole("button", { name: "Show password" }));
        fireEvent.click(screen.getByRole("button", { name: "Hide password" }));

        expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
        expect(screen.getByRole("button", { name: "Show password" })).toHaveAttribute("aria-pressed", "false");
    });

    it("is reachable by keyboard: both the input and the toggle are real, focusable, tabbable elements", () => {
        render(<PasswordField aria-label="Password" {...TOGGLE_LABELS} />);

        const input = screen.getByLabelText("Password");
        const toggle = screen.getByRole("button", { name: "Show password" });

        input.focus();
        expect(input).toHaveFocus();
        expect(toggle.tagName).toBe("BUTTON");
        expect(toggle).toHaveAttribute("type", "button");
        expect(toggle).not.toHaveAttribute("tabindex", "-1");
    });

    it("never lets a caller override the native type attribute", () => {
        render(
            // @ts-expect-error — `type` is deliberately omitted from this component's public props.
            <PasswordField aria-label="Password" type="email" {...TOGGLE_LABELS} />,
        );
        expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
    });

    it("forwards aria-invalid to the real input so the danger-border treatment applies", () => {
        render(<PasswordField aria-label="Password" aria-invalid="true" {...TOGGLE_LABELS} />);
        expect(screen.getByLabelText("Password")).toHaveAttribute("aria-invalid", "true");
    });

    it("forwards disabled to the real input", () => {
        render(<PasswordField aria-label="Password" disabled {...TOGGLE_LABELS} />);
        expect(screen.getByLabelText("Password")).toBeDisabled();
    });

    it("matches the toggle's size to the input's own size", () => {
        render(<PasswordField aria-label="Password" size="lg" {...TOGGLE_LABELS} />);

        expect(screen.getByLabelText("Password")).toHaveClass("h-(--control-height-lg)");
        expect(screen.getByRole("button", { name: "Show password" })).toHaveClass("h-(--control-height-lg)");
    });

    it("applies a caller-supplied className to the outer wrapper, for layout only", () => {
        render(<PasswordField aria-label="Password" className="mt-stack" {...TOGGLE_LABELS} />);
        expect(screen.getByLabelText("Password").parentElement).toHaveClass("mt-stack");
    });

    describe("as the child of Field", () => {
        it("associates Field's label with the real input, and keeps the toggle working", () => {
            render(
                <Field label="Password">
                    <PasswordField {...TOGGLE_LABELS} />
                </Field>,
            );

            const input = screen.getByLabelText("Password");
            expect(input).toHaveAttribute("type", "password");

            fireEvent.click(screen.getByRole("button", { name: "Show password" }));
            expect(screen.getByLabelText("Password")).toHaveAttribute("type", "text");
        });

        it("carries Field's error into aria-invalid on the real input", () => {
            render(
                <Field label="Password" error="Too short">
                    <PasswordField {...TOGGLE_LABELS} />
                </Field>,
            );

            expect(screen.getByLabelText("Password")).toHaveAttribute("aria-invalid", "true");
        });
    });
});
