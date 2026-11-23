import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Track, type TrackStop } from "./Track";

const STOPS: readonly TrackStop[] = [
    { key: "a", label: "Kotlin", state: "done" },
    { key: "b", label: "Payments", state: "now" },
    { key: "c", label: "System design", state: "ahead" },
    { key: "d", label: "Staff", state: "end" },
];

describe("Track", () => {
    it("is an ordered list named by its label, with one item per stop", () => {
        render(<Track stops={STOPS} label="Route to Staff" />);

        expect(screen.getByRole("list", { name: "Route to Staff" })).toBeInTheDocument();
        expect(screen.getAllByRole("listitem")).toHaveLength(4);
    });

    it("marks only the next stop as the current step", () => {
        render(<Track stops={STOPS} label="Route" />);

        const current = screen.getAllByRole("listitem").filter((item) => item.getAttribute("aria-current") === "step");
        expect(current).toHaveLength(1);
        expect(current[0]).toHaveTextContent("Payments");
    });

    it("draws the line that arrives at a reached stop solid and the one at a stop ahead faint", () => {
        render(<Track stops={STOPS} label="Route" />);

        const items = screen.getAllByRole("listitem");
        expect(items[0]?.className).not.toContain("before:border-");
        expect(items[1]?.className).toContain("before:border-interactive-primary");
        expect(items[2]?.className).toContain("before:border-border-default");
    });
});
