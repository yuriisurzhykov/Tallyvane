import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { RadioGroup } from "./RadioGroup";
import { Radio } from "../radio";

function WorkModeGroup(props: { readonly defaultValue?: string } = {}) {
    return (
        <RadioGroup aria-label="Work mode" { ...props }>
            <Radio aria-label="Remote" value="remote"/>
            <Radio aria-label="Hybrid" value="hybrid"/>
            <Radio aria-label="On-site" value="onsite"/>
        </RadioGroup>
    );
}

describe("RadioGroup", () => {
    it("renders every child radio by role and name", () => {
        render(<WorkModeGroup/>);

        expect(screen.getByRole("radio", { name: "Remote" })).toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "Hybrid" })).toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "On-site" })).toBeInTheDocument();
    });

    it("ArrowDown moves both focus and selection to the next radio — Base UI's own composite roving tabindex, not native same-name radio grouping", async () => {
        render(<WorkModeGroup defaultValue="remote"/>);
        const remote = screen.getByRole("radio", { name: "Remote" });
        remote.focus();

        fireEvent.keyDown(remote, { key: "ArrowDown" });

        // Base UI moves focus imperatively from inside a layout effect that
        // runs after the keydown handler's state update commits, not
        // synchronously inside the handler itself — confirmed empirically:
        // a synchronous assertion right after `fireEvent.keyDown` here
        // failed even though the roving `tabindex` had already moved.
        const hybrid = screen.getByRole("radio", { name: "Hybrid" });
        await waitFor(() => expect(hybrid).toHaveFocus());
        expect(hybrid).toBeChecked();
        expect(remote).not.toBeChecked();
    });

    it("ArrowUp moves both focus and selection to the previous radio", async () => {
        render(<WorkModeGroup defaultValue="hybrid"/>);
        const hybrid = screen.getByRole("radio", { name: "Hybrid" });
        hybrid.focus();

        fireEvent.keyDown(hybrid, { key: "ArrowUp" });

        const remote = screen.getByRole("radio", { name: "Remote" });
        await waitFor(() => expect(remote).toHaveFocus());
        expect(remote).toBeChecked();
    });

    it("keeps only the currently focused radio in the tab sequence (roving tabindex)", () => {
        render(<WorkModeGroup defaultValue="remote"/>);

        expect(screen.getByRole("radio", { name: "Remote" })).toHaveAttribute("tabindex", "0");
        expect(screen.getByRole("radio", { name: "Hybrid" })).toHaveAttribute("tabindex", "-1");
        expect(screen.getByRole("radio", { name: "On-site" })).toHaveAttribute("tabindex", "-1");
    });
});
