import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Image } from "./Image";

describe("Image", () => {
    it("provides accessible, asynchronously decoded, lazy-loaded media by default", () => {
        render(<Image src="data:image/svg+xml,%3Csvg/%3E" alt="Authenticator setup QR" />);

        const image = screen.getByRole("img", { name: "Authenticator setup QR" });
        expect(image).toHaveAttribute("loading", "lazy");
        expect(image).toHaveAttribute("decoding", "async");
        expect(image).toHaveAttribute("draggable", "false");
    });
});
