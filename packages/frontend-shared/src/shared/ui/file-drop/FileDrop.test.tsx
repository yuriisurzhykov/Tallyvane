import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { FileDrop } from "./FileDrop";

function renderFileDrop(props: Partial<Parameters<typeof FileDrop>[0]> = {}) {
    const onFileChange = vi.fn();
    render(
        <FileDrop
            label="Drag and drop your résumé here"
            browseLabel="Browse files"
            clearLabel="Remove selected file"
            onFileChange={ onFileChange }
            { ...props }
        />,
    );
    return { onFileChange };
}

function dropZone(): HTMLElement {
    // The instructional text is only present in the idle state, which every
    // test starts from — a stable way to reach the drop zone `<div>` itself
    // without depending on a test id this component doesn't otherwise need.
    return screen.getByText("Drag and drop your résumé here").closest("div")!;
}

function makeFile(name: string, content = "file contents"): File {
    return new File([content], name, { type: "application/pdf" });
}

describe("FileDrop", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("renders the idle prompt and a Browse button", () => {
        renderFileDrop();

        expect(screen.getByText("Drag and drop your résumé here")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Browse files" })).toBeInTheDocument();
    });

    it("exposes a real, focusable file input as the keyboard-equivalent path", () => {
        renderFileDrop();

        const input = screen.getByLabelText("Browse files", { selector: "input" });
        expect(input).toHaveAttribute("type", "file");
        expect(input).toHaveClass("sr-only");
        expect(input.tabIndex).not.toBe(-1);
    });

    it("opens the file dialog when the drop zone is clicked", () => {
        const clickSpy = vi.spyOn(HTMLInputElement.prototype, "click");
        renderFileDrop();

        fireEvent.click(dropZone());

        expect(clickSpy).toHaveBeenCalledTimes(1);
    });

    it("opens the file dialog exactly once when the Browse button is clicked, despite sitting inside the drop zone", () => {
        const clickSpy = vi.spyOn(HTMLInputElement.prototype, "click");
        renderFileDrop();

        fireEvent.click(screen.getByRole("button", { name: "Browse files" }));

        expect(clickSpy).toHaveBeenCalledTimes(1);
    });

    it("does not open the file dialog when disabled", () => {
        const clickSpy = vi.spyOn(HTMLInputElement.prototype, "click");
        renderFileDrop({ disabled: true });

        fireEvent.click(dropZone());

        expect(clickSpy).not.toHaveBeenCalled();
        expect(dropZone()).toHaveAttribute("data-disabled");
    });

    // `aria-disabled`, not just `data-disabled`: the latter only drives the
    // CSS opacity treatment. Without the former, a screen reader gets no
    // signal the whole zone is inactive at all — and, per WCAG 1.4.3, only
    // a real `aria-disabled`/`disabled` marks this text as an inactive
    // component's, exempt from the 4.5:1 contrast minimum a `data-*`
    // attribute axe doesn't recognise cannot claim.
    it("marks the drop zone aria-disabled when disabled", () => {
        renderFileDrop({ disabled: true });

        expect(dropZone()).toHaveAttribute("aria-disabled", "true");
    });

    it("reports the picked file, and shows its name, when chosen via the native input", () => {
        const { onFileChange } = renderFileDrop();
        const input = screen.getByLabelText("Browse files", { selector: "input" });
        const file = makeFile("resume.pdf");

        fireEvent.change(input, { target: { files: [file] } });

        expect(onFileChange).toHaveBeenCalledWith(file);
        expect(screen.getByText("resume.pdf")).toBeInTheDocument();
        expect(screen.queryByText("Drag and drop your résumé here")).not.toBeInTheDocument();
    });

    it("sets data-dragging-over on a real dragenter, and clears it on drop", () => {
        renderFileDrop();
        const zone = dropZone();
        const file = makeFile("resume.pdf");

        fireEvent.dragEnter(zone, { dataTransfer: { files: [file] } });
        expect(zone).toHaveAttribute("data-dragging-over");

        fireEvent.drop(zone, { dataTransfer: { files: [file] } });
        expect(zone).not.toHaveAttribute("data-dragging-over");
    });

    it("does not flicker the active-drag state when the pointer crosses a child element — a real dragenter/dragleave pair on a child, not a fake prop", () => {
        renderFileDrop();
        const zone = dropZone();
        const child = screen.getByText("Drag and drop your résumé here");
        const file = makeFile("resume.pdf");

        fireEvent.dragEnter(zone, { dataTransfer: { files: [file] } });
        expect(zone).toHaveAttribute("data-dragging-over");

        // Entering, then leaving, a child inside the still-hovered zone —
        // exercises the depth counter, not a boolean that would drop to
        // false here even though the pointer never actually left the zone.
        fireEvent.dragEnter(child, { dataTransfer: { files: [file] } });
        fireEvent.dragLeave(child, { dataTransfer: { files: [file] } });
        expect(zone).toHaveAttribute("data-dragging-over");

        fireEvent.dragLeave(zone, { dataTransfer: { files: [file] } });
        expect(zone).not.toHaveAttribute("data-dragging-over");
    });

    it("selects the dropped file", () => {
        const { onFileChange } = renderFileDrop();
        const zone = dropZone();
        const file = makeFile("cover-letter.pdf");

        fireEvent.dragEnter(zone, { dataTransfer: { files: [file] } });
        fireEvent.drop(zone, { dataTransfer: { files: [file] } });

        expect(onFileChange).toHaveBeenCalledWith(file);
        expect(screen.getByText("cover-letter.pdf")).toBeInTheDocument();
    });

    it("ignores drag and drop entirely when disabled", () => {
        const { onFileChange } = renderFileDrop({ disabled: true });
        const zone = dropZone();
        const file = makeFile("resume.pdf");

        fireEvent.dragEnter(zone, { dataTransfer: { files: [file] } });
        expect(zone).not.toHaveAttribute("data-dragging-over");

        fireEvent.drop(zone, { dataTransfer: { files: [file] } });
        expect(onFileChange).not.toHaveBeenCalled();
    });

    it("clears the selected file without reopening the dialog", () => {
        const clickSpy = vi.spyOn(HTMLInputElement.prototype, "click");
        const { onFileChange } = renderFileDrop();
        const input = screen.getByLabelText("Browse files", { selector: "input" });
        fireEvent.change(input, { target: { files: [makeFile("resume.pdf")] } });
        clickSpy.mockClear();

        fireEvent.click(screen.getByRole("button", { name: "Remove selected file" }));

        expect(onFileChange).toHaveBeenLastCalledWith(null);
        expect(screen.queryByText("resume.pdf")).not.toBeInTheDocument();
        expect(screen.getByText("Drag and drop your résumé here")).toBeInTheDocument();
        expect(clickSpy).not.toHaveBeenCalled();
    });
});
