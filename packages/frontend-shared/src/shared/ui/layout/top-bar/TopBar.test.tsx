import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { TopBar } from "./TopBar";

describe("TopBar", () => {
    it("renders the title as the page's one real heading", () => {
        render(<TopBar title="Today" />);

        expect(screen.getByRole("heading", { level: 1, name: "Today" })).toBeInTheDocument();
    });

    it("renders nothing in the actions region when none is supplied", () => {
        render(<TopBar title="Today" />);

        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it("renders supplied actions beside the title", () => {
        render(<TopBar title="Today" actions={<button type="button">Refresh</button>} />);

        expect(screen.getByRole("button", { name: "Refresh" })).toBeInTheDocument();
    });

    it("is a labelled banner landmark", () => {
        render(<TopBar title="Today" />);

        expect(screen.getByRole("banner")).toBeInTheDocument();
    });
});
