import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Form } from "./Form";
import { Field } from "../field";
import { Input } from "../input";

describe("Form", () => {
    it("fires its submission handler on submit", () => {
        const onSubmit = vi.fn((event: React.FormEvent<HTMLFormElement>) => event.preventDefault());
        render(
            <Form onSubmit={onSubmit}>
                <button type="submit">Submit</button>
            </Form>,
        );

        fireEvent.click(screen.getByRole("button", { name: "Submit" }));

        expect(onSubmit).toHaveBeenCalledTimes(1);
    });

    it("applies its default field-to-field layout classes", () => {
        render(
            <Form data-testid="form">
                <button type="submit">Submit</button>
            </Form>,
        );

        expect(screen.getByTestId("form")).toHaveClass("flex", "flex-col", "gap-stack");
    });

    it("appends a caller-supplied className", () => {
        render(
            <Form className="mt-stack" data-testid="form">
                <button type="submit">Submit</button>
            </Form>,
        );

        expect(screen.getByTestId("form")).toHaveClass("mt-stack");
        expect(screen.getByTestId("form")).toHaveClass("gap-stack");
    });

    /**
     * `Field` does not read `Form`'s `errors` context automatically (see
     * `Form.tsx`'s own doc comment) — this mirrors the real call-site pattern:
     * the caller reads `errors[name]` itself and threads it into `Field`'s
     * `error` prop after a (here, faked) server round trip.
     */
    function ExampleForm({ onSubmit }: { readonly onSubmit: () => void }) {
        const [errors, setErrors] = useState<Record<string, string>>({});
        const emailError = errors.email;

        return (
            <Form
                errors={errors}
                onSubmit={(event) => {
                    event.preventDefault();
                    onSubmit();
                    setErrors({ email: "This email is already registered" });
                }}
            >
                <Field label="Email" {...(emailError ? { error: emailError } : {})}>
                    <Input name="email" type="email" />
                </Field>
                <button type="submit">Submit</button>
            </Form>
        );
    }

    it("integration — Form + Field + Input: a server error passed through Form's errors reaches the nested Field.Error, and Input is marked invalid", () => {
        const onSubmit = vi.fn();
        render(<ExampleForm onSubmit={onSubmit} />);

        const input = screen.getByLabelText("Email");
        expect(input).toBeInTheDocument();
        expect(input).not.toHaveAttribute("aria-invalid");
        expect(screen.queryByText("This email is already registered")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Submit" }));

        expect(onSubmit).toHaveBeenCalledTimes(1);
        expect(screen.getByText("This email is already registered")).toBeInTheDocument();
        expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
        expect(screen.getByLabelText("Email")).toHaveClass("aria-invalid:border-status-danger");
    });
});
