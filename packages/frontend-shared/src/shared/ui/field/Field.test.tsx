import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Field } from "./Field";

describe("Field", () => {
    it("associates the label with its control", () => {
        render(
            <Field label="Email">
                <input type="email" />
            </Field>,
        );

        expect(screen.getByLabelText("Email")).toBeInTheDocument();
    });

    it("shows the description when no error is present", () => {
        render(
            <Field label="Email" description="We only use this to send interview invites">
                <input type="email" />
            </Field>,
        );

        expect(screen.getByText("We only use this to send interview invites")).toBeInTheDocument();
    });

    it("shows the error, and hides the description, when both are passed", () => {
        render(
            <Field
                label="Email"
                description="We only use this to send interview invites"
                error="Enter a valid email address"
            >
                <input type="email" />
            </Field>,
        );

        expect(screen.getByText("Enter a valid email address")).toBeInTheDocument();
        expect(screen.queryByText("We only use this to send interview invites")).not.toBeInTheDocument();
    });

    it("marks the control invalid when an error is passed", () => {
        render(
            <Field label="Email" error="Enter a valid email address">
                <input type="email" />
            </Field>,
        );

        expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    });

    it("does not mark the control invalid when no error is passed", () => {
        render(
            <Field label="Email">
                <input type="email" />
            </Field>,
        );

        expect(screen.getByLabelText("Email")).not.toHaveAttribute("aria-invalid");
    });

    it("marks the control as required", () => {
        render(
            <Field label="Email" required>
                <input type="email" />
            </Field>,
        );

        expect(screen.getByLabelText("Email")).toBeRequired();
    });

    it("does not mark the control as required by default", () => {
        render(
            <Field label="Email">
                <input type="email" />
            </Field>,
        );

        expect(screen.getByLabelText("Email")).not.toBeRequired();
    });
});
