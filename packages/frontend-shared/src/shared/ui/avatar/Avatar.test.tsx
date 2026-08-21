import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Avatar } from "./Avatar";

/**
 * Base UI's own `useImageLoadingStatus` (verified by reading
 * `useImageLoadingStatus.js`) drives `imageLoadingStatus` from a detached
 * probe `new window.Image()` it constructs internally — never from the
 * `<img>` element `Avatar.Image` actually renders — so `fireEvent.load`/
 * `.error` on the rendered element has no effect on it. jsdom's own `Image`
 * also never fires `onload`/`onerror` on its own (no real network), so the
 * "loading genuinely succeeds" and "loading genuinely fails" paths below
 * stub `window.Image` for one test each; every other test relies on
 * jsdom's true default behaviour (a `src` that is set but never resolves)
 * with no stubbing at all.
 */
class ImmediatelyLoadedImage {
    public onload: (() => void) | null = null;
    public onerror: (() => void) | null = null;
    public complete = false;
    public naturalWidth = 0;
    public crossOrigin: string | null = null;

    public set src(_value: string) {
        this.onload?.();
    }
}

class ImmediatelyFailingImage {
    public onload: (() => void) | null = null;
    public onerror: (() => void) | null = null;
    public complete = false;
    public naturalWidth = 0;
    public crossOrigin: string | null = null;

    public set src(_value: string) {
        this.onerror?.();
    }
}

describe("Avatar", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("renders only the fallback when no image is provided at all", () => {
        render(
            <Avatar.Root>
                <Avatar.Fallback>AB</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByText("AB")).toBeInTheDocument();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });

    it("renders the fallback, and not the image, while a load never resolves (jsdom's true default — no stubbing)", () => {
        render(
            <Avatar.Root>
                <Avatar.Image src="https://example.test/avatar.png" alt="Ada Lovelace"/>
                <Avatar.Fallback>AL</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByText("AL")).toBeInTheDocument();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });

    it("renders the image, and hides the fallback, once loading actually succeeds", () => {
        vi.stubGlobal("Image", ImmediatelyLoadedImage);
        render(
            <Avatar.Root>
                <Avatar.Image src="https://example.test/avatar.png" alt="Ada Lovelace"/>
                <Avatar.Fallback>AL</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByRole("img", { name: "Ada Lovelace" })).toBeInTheDocument();
        expect(screen.queryByText("AL")).not.toBeInTheDocument();
    });

    it("keeps showing the fallback when a real load attempt fails", () => {
        vi.stubGlobal("Image", ImmediatelyFailingImage);
        render(
            <Avatar.Root>
                <Avatar.Image src="https://example.test/broken.png" alt="Ada Lovelace"/>
                <Avatar.Fallback>AL</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByText("AL")).toBeInTheDocument();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });

    it("defaults Root to the md control-height size", () => {
        render(
            <Avatar.Root>
                <Avatar.Fallback>AB</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByText("AB").parentElement).toHaveClass("h-(--control-height-md)");
    });

    it.each(["sm", "md", "lg"] as const)('applies the "%s" control-height class to Root', (size) => {
        render(
            <Avatar.Root size={ size }>
                <Avatar.Fallback>AB</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByText("AB").parentElement).toHaveClass(`h-(--control-height-${ size })`, `w-(--control-height-${ size })`);
    });

    it("applies the pill radius to Root, the same rounding Dot uses for the other round thing", () => {
        render(
            <Avatar.Root>
                <Avatar.Fallback>AB</Avatar.Fallback>
            </Avatar.Root>,
        );

        expect(screen.getByText("AB").parentElement).toHaveClass("rounded-pill");
    });

    it("merges a caller-provided className on Root and Fallback with their own classes", () => {
        render(
            <Avatar.Root className="mt-stack">
                <Avatar.Fallback className="uppercase">ab</Avatar.Fallback>
            </Avatar.Root>,
        );

        const fallback = screen.getByText("ab");
        expect(fallback).toHaveClass("uppercase");
        expect(fallback.parentElement).toHaveClass("rounded-pill");
        expect(fallback.parentElement).toHaveClass("mt-stack");
    });
});
