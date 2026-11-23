import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { Icon } from "./Icon";

describe("Icon", () => {
    it("renders a decorative glyph, hidden from assistive technology", () => {
        const { container } = render(<Icon name="mic" />);
        const glyph = container.querySelector("svg");

        expect(glyph).not.toBeNull();
        expect(glyph).toHaveAttribute("aria-hidden", "true");
        expect(glyph).toHaveClass("size-stack");
    });

    it("passes layout classes through", () => {
        const { container } = render(<Icon name="back" className="shrink-0" />);

        expect(container.querySelector("svg")).toHaveClass("shrink-0");
    });
});
