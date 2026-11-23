import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Composer } from "./Composer";

function setup(value: string, busy = false) {
    const onSubmit = vi.fn();
    const onChange = vi.fn();
    render(
        <Composer
            label="What's new"
            placeholder="Tell me"
            value={value}
            onChange={onChange}
            onSubmit={onSubmit}
            submitLabel="Send"
            busy={busy}
        />,
    );
    return { onSubmit, onChange };
}

describe("Composer", () => {
    it("reports what is typed", () => {
        const { onChange } = setup("");

        fireEvent.change(screen.getByLabelText("What's new"), { target: { value: "hi" } });

        expect(onChange).toHaveBeenCalledWith("hi");
    });

    it("sends on Enter and not on Shift+Enter", () => {
        const { onSubmit } = setup("hello");
        const field = screen.getByLabelText("What's new");

        fireEvent.keyDown(field, { key: "Enter", shiftKey: true });
        expect(onSubmit).not.toHaveBeenCalled();

        fireEvent.keyDown(field, { key: "Enter" });
        expect(onSubmit).toHaveBeenCalledTimes(1);
    });

    it("sends nothing while empty or busy, and disables the button", () => {
        const empty = setup("   ");
        fireEvent.keyDown(screen.getByLabelText("What's new"), { key: "Enter" });
        expect(empty.onSubmit).not.toHaveBeenCalled();
        expect(screen.getByRole("button", { name: "Send" })).toBeDisabled();
    });

    it("does not send again while a send is under way", () => {
        const busy = setup("hello", true);
        fireEvent.keyDown(screen.getByLabelText("What's new"), { key: "Enter" });
        expect(busy.onSubmit).not.toHaveBeenCalled();
    });
});
