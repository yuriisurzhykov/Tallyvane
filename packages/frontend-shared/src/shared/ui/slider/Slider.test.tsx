import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { Slider } from "./Slider";

describe("Slider", () => {
    it("renders a single slider with min/max/current value exposed for assistive tech", () => {
        render(<Slider aria-label="Weekly application goal" defaultValue={ 5 } min={ 0 } max={ 20 }/>);
        const slider = screen.getByRole("slider", { name: "Weekly application goal" });

        // A real native `<input type="range">`, not a hand-rolled
        // `role="slider"` — its implicit ARIA range comes from the native
        // `min`/`max`/`value` attributes, so there is no separate
        // `aria-valuemin`/`aria-valuemax` to assert on top of them.
        expect(slider).toHaveAttribute("min", "0");
        expect(slider).toHaveAttribute("max", "20");
        expect(slider).toHaveAttribute("aria-valuenow", "5");
    });

    it("supports an uncontrolled default value", () => {
        render(<Slider aria-label="Weekly application goal" defaultValue={ 5 } min={ 0 } max={ 20 }/>);
        expect(screen.getByRole("slider", { name: "Weekly application goal" })).toHaveAttribute("aria-valuenow", "5");
    });

    it("steps up on ArrowRight and down on ArrowLeft", () => {
        render(<Slider aria-label="Weekly application goal" defaultValue={ 5 } min={ 0 } max={ 20 }/>);
        const slider = screen.getByRole("slider", { name: "Weekly application goal" });
        slider.focus();

        fireEvent.keyDown(slider, { key: "ArrowRight" });
        expect(slider).toHaveAttribute("aria-valuenow", "6");

        fireEvent.keyDown(slider, { key: "ArrowLeft" });
        expect(slider).toHaveAttribute("aria-valuenow", "5");
    });

    it("jumps to min on Home and max on End", () => {
        render(<Slider aria-label="Weekly application goal" defaultValue={ 5 } min={ 0 } max={ 20 }/>);
        const slider = screen.getByRole("slider", { name: "Weekly application goal" });
        slider.focus();

        fireEvent.keyDown(slider, { key: "End" });
        expect(slider).toHaveAttribute("aria-valuenow", "20");

        fireEvent.keyDown(slider, { key: "Home" });
        expect(slider).toHaveAttribute("aria-valuenow", "0");
    });

    it("supports a controlled value via value/onValueChange", () => {
        const onValueChange = vi.fn();
        render(<Slider aria-label="Weekly application goal" value={ 5 } min={ 0 } max={ 20 }
                       onValueChange={ onValueChange }/>);
        const slider = screen.getByRole("slider", { name: "Weekly application goal" });
        slider.focus();

        fireEvent.keyDown(slider, { key: "ArrowRight" });

        expect(onValueChange).toHaveBeenCalledWith(6, expect.anything());
        // Still 5 — the caller (via `value`) never flipped it.
        expect(slider).toHaveAttribute("aria-valuenow", "5");
    });

    it("marks every part data-disabled, and the actual input natively disabled", () => {
        render(<Slider aria-label="Weekly application goal" defaultValue={ 5 } min={ 0 } max={ 20 } disabled/>);
        const slider = screen.getByRole("slider", { name: "Weekly application goal" });

        expect(document.querySelector("[data-disabled]")).toBeInTheDocument();
        // The real interaction block is the native `disabled` attribute on
        // this `<input type="range">` — confirmed by reading `SliderThumb.js`
        // directly: unlike `Checkbox`/`Radio`/`Switch`, there is no separate
        // JS-level "if (disabled) return" guard on keydown here, only on
        // pointer/drag start. A real browser refuses focus and keyboard
        // input to a disabled form control natively; `fireEvent.keyDown` in
        // jsdom does not reproduce that browser-level behaviour (it firing
        // one anyway would not indicate a real bug in this component), so
        // this test asserts the one thing actually meaningful to check
        // here rather than a jsdom-only false negative.
        expect(slider).toBeDisabled();
    });
});
