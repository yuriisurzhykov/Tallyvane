import { useState } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { describe, expect, it, vi } from "vitest";
import { InlineEdit, type InlineEditEditorRenderProps, type InlineEditProps } from "./InlineEdit";

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}

/** A minimal text editor — the "actual" editor is the caller's problem, per this component's own render-prop design; this is just enough to exercise the contract. */
function TextEditor({ value, onChange, commit, cancel }: InlineEditEditorRenderProps<string>) {
    return (
        <>
            <input aria-label="Job title" value={value} onChange={(event) => onChange(event.target.value)} />
            <button type="button" onClick={commit}>
                Save
            </button>
            <button type="button" onClick={cancel}>
                Cancel
            </button>
        </>
    );
}

/**
 * Wraps `InlineEdit` the way a real Tier 3+ caller would: `value` lives in
 * the harness's own state, and `onSave` is where a real caller's mutation
 * would go. Exists so tests can assert what the caller's own state ends up
 * holding, not just what `InlineEdit` renders internally.
 */
function Harness({
    initialValue,
    onSave,
    ...rest
}: Partial<Omit<InlineEditProps<string>, "value" | "onSave" | "renderValue" | "renderEditor" | "editLabel">> & {
    readonly initialValue: string;
    readonly onSave: (value: string) => Promise<void>;
}) {
    const [value, setValue] = useState(initialValue);
    return (
        <InlineEdit<string>
            value={value}
            onSave={async (next) => {
                await onSave(next);
                setValue(next);
            }}
            renderValue={(v) => <span>{v}</span>}
            renderEditor={(editorProps) => <TextEditor {...editorProps} />}
            editLabel="Edit job title"
            {...rest}
        />
    );
}

describe("InlineEdit", () => {
    it("renders the display value via renderValue, not the editor, until clicked", () => {
        render(<Harness initialValue="Backend Engineer" onSave={vi.fn().mockResolvedValue(undefined)} />);

        expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
        expect(screen.queryByLabelText("Job title")).not.toBeInTheDocument();
    });

    it("the trigger's accessible name carries both the value and the caller's edit label", () => {
        render(<Harness initialValue="Backend Engineer" onSave={vi.fn().mockResolvedValue(undefined)} />);

        expect(screen.getByRole("button", { name: "Backend Engineer Edit job title" })).toBeInTheDocument();
    });

    it("clicking the trigger switches to the editor, seeded with the current value", () => {
        render(<Harness initialValue="Backend Engineer" onSave={vi.fn().mockResolvedValue(undefined)} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));

        expect(screen.getByLabelText("Job title")).toHaveValue("Backend Engineer");
        expect(screen.queryByText("Backend Engineer", { selector: "span" })).not.toBeInTheDocument();
    });

    it("Enter commits: saves the typed value and returns to the display state showing it", async () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        render(<Harness initialValue="Backend Engineer" onSave={onSave} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        const input = screen.getByLabelText("Job title");
        fireEvent.change(input, { target: { value: "Senior Backend Engineer" } });
        fireEvent.keyDown(input, { key: "Enter" });

        expect(onSave).toHaveBeenCalledExactlyOnceWith("Senior Backend Engineer");
        await waitFor(() => {
            expect(screen.getByText("Senior Backend Engineer")).toBeInTheDocument();
        });
        expect(screen.queryByLabelText("Job title")).not.toBeInTheDocument();
    });

    it("Escape cancels: restores the original value with no save, and returns to the display state", () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        render(<Harness initialValue="Backend Engineer" onSave={onSave} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        const input = screen.getByLabelText("Job title");
        fireEvent.change(input, { target: { value: "Something the user regrets" } });
        fireEvent.keyDown(input, { key: "Escape" });

        expect(onSave).not.toHaveBeenCalled();
        expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
        expect(screen.queryByLabelText("Job title")).not.toBeInTheDocument();
    });

    it("losing focus to somewhere outside the editor also commits", async () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        render(<Harness initialValue="Backend Engineer" onSave={onSave} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        const input = screen.getByLabelText("Job title");
        fireEvent.change(input, { target: { value: "Senior Backend Engineer" } });
        // React's synthetic onBlur is delegated off the bubbling native "focusout", not "blur" — same finding this codebase's own toast test (`ToastRegion.test.tsx`) already documents for `onFocus`/"focusin".
        fireEvent.focusOut(input, { relatedTarget: document.body });

        expect(onSave).toHaveBeenCalledExactlyOnceWith("Senior Backend Engineer");
        await waitFor(() => {
            expect(screen.getByText("Senior Backend Engineer")).toBeInTheDocument();
        });
    });

    it("moving focus between two elements inside the editor (e.g. input to its own Save button) does not commit early", () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        render(<Harness initialValue="Backend Engineer" onSave={onSave} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        const input = screen.getByLabelText("Job title");
        const saveButton = screen.getByRole("button", { name: "Save" });
        fireEvent.change(input, { target: { value: "Senior Backend Engineer" } });
        fireEvent.focusOut(input, { relatedTarget: saveButton });

        expect(onSave).not.toHaveBeenCalled();
        expect(screen.getByLabelText("Job title")).toBeInTheDocument();
    });

    it("returns keyboard focus to the trigger after Escape", () => {
        render(<Harness initialValue="Backend Engineer" onSave={vi.fn().mockResolvedValue(undefined)} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        fireEvent.keyDown(screen.getByLabelText("Job title"), { key: "Escape" });

        expect(screen.getByRole("button", { name: "Backend Engineer Edit job title" })).toHaveFocus();
    });

    /**
     * The product-level spec (`ARCHITECTURE.md` §12.9): saving happens
     * automatically 400ms after the last keystroke, with no explicit commit
     * at all. Fake timers are scoped to this one test, not applied globally
     * (as `use-debounced-autosave.test.ts` does) — every other test here
     * commits through `flush()` (Enter, blur), which does not depend on the
     * timer, and `@testing-library/react`'s own `waitFor` polls with a real
     * `setInterval` that fake timers would otherwise stall.
     */
    it("autosaves ~400ms after the last change even without pressing Enter", async () => {
        vi.useFakeTimers();
        try {
            const onSave = vi.fn().mockResolvedValue(undefined);
            render(<Harness initialValue="Backend Engineer" onSave={onSave} />);

            fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
            fireEvent.change(screen.getByLabelText("Job title"), { target: { value: "Senior Backend Engineer" } });

            expect(onSave).not.toHaveBeenCalled();
            await act(async () => {
                await vi.advanceTimersByTimeAsync(400);
            });
            expect(onSave).toHaveBeenCalledExactlyOnceWith("Senior Backend Engineer");
        } finally {
            vi.useRealTimers();
        }
    });

    it("a failed save reports the error through onError together with a working retry, and keeps showing the typed value", async () => {
        const failure = new Error("network down");
        const onSave = vi.fn().mockRejectedValueOnce(failure).mockResolvedValueOnce(undefined);
        const onError = vi.fn();
        render(<Harness initialValue="Backend Engineer" onSave={onSave} onError={onError} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        fireEvent.change(screen.getByLabelText("Job title"), { target: { value: "Senior Backend Engineer" } });
        fireEvent.keyDown(screen.getByLabelText("Job title"), { key: "Enter" });

        await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
        expect(onError.mock.calls[0]?.[0]).toBe(failure);
        // The exit was optimistic — the typed value still shows even though the save behind it failed.
        expect(screen.getByText("Senior Backend Engineer")).toBeInTheDocument();

        const retry = onError.mock.calls[0]?.[1] as () => void;
        await act(() => {
            retry();
            return Promise.resolve();
        });

        expect(onSave).toHaveBeenCalledTimes(2);
        expect(onSave.mock.calls[1]?.[0]).toBe("Senior Backend Engineer");
    });

    it("marks the trigger with a danger border while the last save is unresolved, clearing once it succeeds", async () => {
        const failure = new Error("network down");
        const onSave = vi.fn().mockRejectedValueOnce(failure).mockResolvedValueOnce(undefined);
        const onError = vi.fn();
        render(<Harness initialValue="Backend Engineer" onSave={onSave} onError={onError} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        fireEvent.change(screen.getByLabelText("Job title"), { target: { value: "Senior Backend Engineer" } });
        fireEvent.keyDown(screen.getByLabelText("Job title"), { key: "Enter" });
        await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));

        const trigger = screen.getByRole("button", { name: "Senior Backend Engineer Edit job title" });
        expect(trigger).toHaveClass("border-status-danger");

        const retry = onError.mock.calls[0]?.[1] as () => void;
        await act(() => {
            retry();
            return Promise.resolve();
        });

        expect(screen.getByRole("button", { name: "Senior Backend Engineer Edit job title" })).not.toHaveClass("border-status-danger");
    });

    it("clicking back into the trigger after a failure re-opens the editor with the still-unsaved value", async () => {
        const onSave = vi.fn().mockRejectedValue(new Error("still down"));
        render(<Harness initialValue="Backend Engineer" onSave={onSave} onError={vi.fn()} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        fireEvent.change(screen.getByLabelText("Job title"), { target: { value: "Senior Backend Engineer" } });
        fireEvent.keyDown(screen.getByLabelText("Job title"), { key: "Enter" });
        await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));

        fireEvent.click(screen.getByRole("button", { name: "Senior Backend Engineer Edit job title" }));

        expect(screen.getByLabelText("Job title")).toHaveValue("Senior Backend Engineer");
    });

    it("resolves onSave's own promise before any state update, guarding against an unmount race", async () => {
        const { promise, resolve } = deferred<void>();
        const onSave = vi.fn().mockReturnValue(promise);
        const { unmount } = render(<Harness initialValue="Backend Engineer" onSave={onSave} />);

        fireEvent.click(screen.getByRole("button", { name: "Backend Engineer Edit job title" }));
        fireEvent.change(screen.getByLabelText("Job title"), { target: { value: "Senior Backend Engineer" } });
        fireEvent.keyDown(screen.getByLabelText("Job title"), { key: "Enter" });

        unmount();
        await act(async () => {
            resolve();
            await promise;
        });
        // No assertion beyond "this does not throw / warn" — the point of this test is the unmount race itself.
    });
});
