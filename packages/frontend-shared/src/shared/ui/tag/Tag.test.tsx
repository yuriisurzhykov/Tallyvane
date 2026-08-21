import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Tag, type TagTone } from "./Tag";

const TONE_CLASS: Record<TagTone, string> = {
    neutral: "bg-surface-inset",
    info: "bg-status-info-subtle",
    attention: "bg-status-attention-subtle",
    success: "bg-status-success-subtle",
    danger: "bg-status-danger-subtle",
};

describe("Tag", () => {
    describe.each(Object.entries(TONE_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`renders with its "${ expectedClass }" background`, () => {
            render(
                <Tag tone={ tone as TagTone } onRemove={ () => {
                } } removeLabel="Remove React">
                    React
                </Tag>,
            );
            expect(screen.getByText("React")).toHaveClass(expectedClass);
        });
    });

    it("defaults to the neutral tone when none is given", () => {
        render(
            <Tag onRemove={ () => {
            } } removeLabel="Remove React">
                React
            </Tag>,
        );
        expect(screen.getByText("React")).toHaveClass("bg-surface-inset");
    });

    it("reads its shape from the chip radius role, not Badge's pill", () => {
        render(
            <Tag onRemove={ () => {
            } } removeLabel="Remove React">
                React
            </Tag>,
        );
        expect(screen.getByText("React")).toHaveClass("rounded-chip");
    });

    it("renders a real, named dismiss button", () => {
        render(
            <Tag onRemove={ () => {
            } } removeLabel="Remove React">
                React
            </Tag>,
        );
        const button = screen.getByRole("button", { name: "Remove React" });
        expect(button).toHaveAttribute("type", "button");
    });

    it("calls onRemove exactly once when the dismiss button is clicked", () => {
        const onRemove = vi.fn();
        render(
            <Tag onRemove={ onRemove } removeLabel="Remove React">
                React
            </Tag>,
        );

        // `{ detail: 1 }`: jsdom's default `MouseEvent.detail: 0` is a real
        // mouse click's marker for code that branches on it — see
        // `.cursor/rules/lessons-learned.mdc`'s dated entry. This button is a
        // plain native `<button>` with no such branching, so it does not
        // matter here in practice; set explicitly anyway so this test does
        // not quietly rely on jsdom's default being irrelevant by luck.
        fireEvent.click(screen.getByRole("button", { name: "Remove React" }), { detail: 1 });
        expect(onRemove).toHaveBeenCalledTimes(1);
    });

    it("is a real, natively focusable button — keyboard activation is standard browser behaviour, not custom-implemented here", () => {
        render(
            <Tag onRemove={ () => {
            } } removeLabel="Remove React">
                React
            </Tag>,
        );
        const button = screen.getByRole("button", { name: "Remove React" });
        expect(button.tagName).toBe("BUTTON");
        expect(button).not.toHaveAttribute("tabindex", "-1");
    });

    it("carries the focus-visible ring utility on its dismiss button, required of every interactive component in this system", () => {
        render(
            <Tag onRemove={ () => {
            } } removeLabel="Remove React">
                React
            </Tag>,
        );
        expect(screen.getByRole("button", { name: "Remove React" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(
            <Tag onRemove={ () => {
            } } removeLabel="Remove React" className="ml-inline-tight">
                React
            </Tag>,
        );
        const tag = screen.getByText("React");
        expect(tag).toHaveClass("bg-surface-inset");
        expect(tag).toHaveClass("ml-inline-tight");
    });
});
