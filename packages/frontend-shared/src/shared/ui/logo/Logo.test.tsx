import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Logo } from "./Logo";

describe("Logo", () => {
    it("renders the passed text in the accessible tree", () => {
        render(<Logo text="Acme Corp" />);

        expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    // Regression guard for ARCHITECTURE.md §13.4: the product name must never
    // be hardcoded here, only ever passed in as `text` by the caller.
    it("never hardcodes the real product name in its own source", () => {
        const sourcePath = join(dirname(fileURLToPath(import.meta.url)), "Logo.tsx");
        const source = readFileSync(sourcePath, "utf-8");

        expect(source).not.toMatch(/tallyvane/i);
    });
});
