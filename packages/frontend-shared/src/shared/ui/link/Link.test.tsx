import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Link } from "./Link";

describe("Link", () => {
    it("renders as a real anchor by default", () => {
        render(<Link href="/jobs">View jobs</Link>);

        const link = screen.getByRole("link", { name: "View jobs" });
        expect(link.tagName).toBe("A");
        expect(link).toHaveAttribute("href", "/jobs");
    });

    it("carries its own text-colour and underline classes", () => {
        render(<Link href="/jobs">View jobs</Link>);

        const link = screen.getByRole("link", { name: "View jobs" });
        expect(link).toHaveClass("text-interactive-primary-text");
        expect(link).toHaveClass("underline");
    });

    it("carries the focus-visible ring utility, required of every interactive component in this system", () => {
        render(<Link href="/jobs">View jobs</Link>);
        expect(screen.getByRole("link", { name: "View jobs" })).toHaveClass("focus-visible:focus-ring");
    });

    it("renders through the render prop when the caller opts into a different element", () => {
        render(
            <Link render={<span />} href="/jobs">
                View jobs
            </Link>,
        );

        const element = screen.getByText("View jobs");
        expect(element.tagName).toBe("SPAN");
        expect(element).toHaveClass("text-interactive-primary-text");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(
            <Link href="/jobs" className="mt-stack">
                View jobs
            </Link>,
        );

        const link = screen.getByRole("link", { name: "View jobs" });
        expect(link).toHaveClass("underline");
        expect(link).toHaveClass("mt-stack");
    });
});
