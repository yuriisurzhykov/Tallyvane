import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { QrCode } from "./QrCode";

describe("QrCode", () => {
    it("is one picture with a name, since it carries no text", () => {
        render(<QrCode value="otpauth://totp/Tallyvane" label="QR code for your authenticator app" />);

        expect(screen.getByRole("img", { name: "QR code for your authenticator app" })).toBeInTheDocument();
    });

    it("draws the matrix and its four-module border: a version 1 code is 21 modules, so 29 across", () => {
        render(<QrCode value="HELLO" label="code" />);

        expect(screen.getByRole("img")).toHaveAttribute("viewBox", "0 0 29 29");
    });

    it("draws dark modules as one path, with none of them in the border", () => {
        render(<QrCode value="HELLO" label="code" />);

        const path = screen.getByRole("img").querySelector("path");
        const corners = [...(path?.getAttribute("d") ?? "").matchAll(/M(\d+) (\d+)h1v1h-1z/g)].map((match) => [Number(match[1]), Number(match[2])]);
        expect(corners.length).toBeGreaterThan(100);
        expect(corners.every(([x, y]) => x !== undefined && y !== undefined && x >= 4 && x < 25 && y >= 4 && y < 25)).toBe(true);
    });

    it("carries a different picture for a different value", () => {
        const first = render(<QrCode value="one" label="code" />);
        const drawn = first.container.querySelector("path")?.getAttribute("d");
        first.unmount();
        const second = render(<QrCode value="two" label="code" />);

        expect(second.container.querySelector("path")?.getAttribute("d")).not.toBe(drawn);
    });

    it("wears the light theme so a dark page does not invert it, and appends a caller's className", () => {
        render(<QrCode value="HELLO" label="code" className="w-40" />);

        expect(screen.getByRole("img")).toHaveClass("theme-light", "w-40");
    });
});
