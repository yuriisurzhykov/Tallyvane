import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { ToastRegion, useToast, type ToastTone } from "./ToastRegion";

function Fire({ label, tone, ...rest }: { readonly label: string; readonly tone?: ToastTone; readonly timeout?: number; readonly onAction?: () => void }) {
    const { actions } = useToast();
    return (
        <button
            onClick={() =>
                actions.add({
                    title: label,
                    ...(tone === undefined ? {} : { tone }),
                    ...(rest.timeout === undefined ? {} : { timeout: rest.timeout }),
                    ...(rest.onAction ? { action: { label: "Undo", onAction: rest.onAction } } : {}),
                })
            }
        >
            Fire {label}
        </button>
    );
}

describe("ToastRegion", () => {
    it("renders no toasts until one is added", () => {
        render(
            <ToastRegion>
                <Fire label="Saved" />
            </ToastRegion>,
        );
        expect(screen.queryByText("Saved")).not.toBeInTheDocument();
    });

    it("adding a toast shows its title", async () => {
        render(
            <ToastRegion>
                <Fire label="Saved" />
            </ToastRegion>,
        );
        fireEvent.click(screen.getByText("Fire Saved"));

        await waitFor(() => {
            expect(screen.getByText("Saved")).toBeInTheDocument();
        });
    });

    /**
     * Definition-of-done load-bearing case: the queue holds several toasts
     * at once, and closing one by id leaves the others exactly as they
     * were — verifying `state.toasts` actually reflects Base UI's own
     * queue (with each toast's real id) rather than a stale local copy.
     *
     * Dismissal is driven through `actions.close(id)` rather than a UI
     * button click here deliberately: Base UI's own viewport collapses a
     * stack of more than one toast by default, marking every button but
     * the front-most one `aria-hidden` until the stack is hovered/expanded
     * (confirmed by rendering — a real, deliberate accessibility behaviour,
     * not a bug) — a UI-level click-to-dismiss path is covered on its own,
     * below, with a single toast where that collapse does not apply.
     */
    it("stacks multiple toasts, and closing one by id leaves the others", async () => {
        function ThreeToasts() {
            const { state, actions } = useToast();
            return (
                <>
                    <button onClick={() => actions.add({ title: "First" })}>Fire First</button>
                    <button onClick={() => actions.add({ title: "Second" })}>Fire Second</button>
                    <button onClick={() => actions.add({ title: "Third" })}>Fire Third</button>
                    <button onClick={() => actions.close(state.toasts.find((t) => t.title === "Second")!.id)}>Close Second</button>
                </>
            );
        }
        render(
            <ToastRegion>
                <ThreeToasts />
            </ToastRegion>,
        );

        fireEvent.click(screen.getByText("Fire First"));
        fireEvent.click(screen.getByText("Fire Second"));
        fireEvent.click(screen.getByText("Fire Third"));

        await waitFor(() => {
            expect(screen.getByText("First")).toBeInTheDocument();
            expect(screen.getByText("Second")).toBeInTheDocument();
            expect(screen.getByText("Third")).toBeInTheDocument();
        });

        fireEvent.click(screen.getByText("Close Second"));

        await waitFor(() => {
            expect(screen.queryByText("Second")).not.toBeInTheDocument();
        });
        expect(screen.getByText("First")).toBeInTheDocument();
        expect(screen.getByText("Third")).toBeInTheDocument();
    });

    /**
     * Base UI's own `Toast.Close` tracks `hasFocus` as local state on the
     * *button itself* (`useState` plus its own `onFocus`/`onBlur`,
     * `ToastClose.js`) and stays `aria-hidden` until either that exact
     * button receives focus or the viewport's stack is "expanded" —
     * deliberately unreachable by a stray tab stop otherwise. That makes
     * it unreachable via `getByRole` (which mirrors the accessibility
     * tree) until it already has focus — a real chicken-and-egg a sighted
     * mouse user resolves by hovering, and a keyboard user by tabbing into
     * the toast region first; this test resolves it by finding the raw DOM
     * node once and focusing it directly. `Toast.Action` carries no such
     * gate, which is why the "renders the action button" test above needs
     * no such step — a real difference between the two, not an
     * inconsistency to paper over.
     *
     * A real DOM `.focus()` call moves `document.activeElement` (confirmed
     * directly) but is not enough on its own here: React's synthetic
     * `onFocus` is delegated off the bubbling native `focusin`, and this
     * jsdom/React combination did not fire one from `.focus()` alone in
     * practice — `fireEvent.focusIn` dispatches it explicitly.
     */
    it("clicking a toast's own Close button dismisses it, once it has focus", async () => {
        render(
            <ToastRegion>
                <Fire label="Saved" />
            </ToastRegion>,
        );
        fireEvent.click(screen.getByText("Fire Saved"));
        await waitFor(() => expect(screen.getByText("Saved")).toBeInTheDocument());

        const closeButton = document.querySelector('[aria-label="Dismiss"]') as HTMLElement;
        closeButton.focus();
        fireEvent.focusIn(closeButton);

        fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));

        await waitFor(() => {
            expect(screen.queryByText("Saved")).not.toBeInTheDocument();
        });
    });

    it("maps tone to a tone-coloured accent border, defaulting to neutral", async () => {
        render(
            <ToastRegion>
                <Fire label="Failed save" tone="danger" />
                <Fire label="Untoned" />
            </ToastRegion>,
        );
        fireEvent.click(screen.getByText("Fire Failed save"));
        fireEvent.click(screen.getByText("Fire Untoned"));

        await waitFor(() => {
            expect(screen.getByText("Failed save").closest(".border-status-danger")).not.toBeNull();
            expect(screen.getByText("Untoned").closest(".border-border-subtle")).not.toBeNull();
        });
    });

    /**
     * A call counter, not a boolean flag: a boolean cannot distinguish
     * "called once" from "called twice", and this component's own first
     * draft passed `{...toast.actionProps}` onto `Toast.Action` in addition
     * to the props it already reads from `toast` context internally
     * (`ToastAction.js` resolves `onClick` from `toast.actionProps` on its
     * own) — composing the same handler into the merged prop chain twice,
     * so one real click fired `onAction` twice. A boolean-flag assertion
     * here would have stayed green through that bug; a real Chromium click
     * in `toast-keyboard.spec.ts` is what actually caught it.
     */
    it("renders the action button and calls onAction exactly once per click, without dismissing on its own", async () => {
        let callCount = 0;
        render(
            <ToastRegion>
                <Fire label="Archived" onAction={() => { callCount += 1; }} />
            </ToastRegion>,
        );
        fireEvent.click(screen.getByText("Fire Archived"));
        await waitFor(() => expect(screen.getByText("Archived")).toBeInTheDocument());

        fireEvent.click(screen.getByRole("button", { name: "Undo" }));

        expect(callCount).toBe(1);
        expect(screen.getByText("Archived")).toBeInTheDocument();
    });

    it("auto-dismisses after its timeout elapses", async () => {
        render(
            <ToastRegion>
                <Fire label="Ephemeral" timeout={50} />
            </ToastRegion>,
        );
        fireEvent.click(screen.getByText("Fire Ephemeral"));
        await waitFor(() => expect(screen.getByText("Ephemeral")).toBeInTheDocument());

        await waitFor(
            () => {
                expect(screen.queryByText("Ephemeral")).not.toBeInTheDocument();
            },
            { timeout: 2000 },
        );
    });

    it("throws when useToast is called outside a ToastRegion", () => {
        function Lonely() {
            useToast();
            return null;
        }
        expect(() => render(<Lonely />)).toThrow(/useToast must be used inside/);
    });

    /**
     * The `meta.manager` seam: the exact same handle a non-React module
     * (an API client's error interceptor, say) would hold and call
     * `.add(...)` on. Verified by grabbing it from one `useToast()` call
     * and using it completely outside any component's render — the toast
     * still reaches the region's queue.
     */
    it("meta.manager can add a toast from outside React, and it reaches the same queue", async () => {
        let capturedManager: ReturnType<typeof useToast>["meta"]["manager"] | undefined;
        function CaptureManager() {
            const { meta } = useToast();
            capturedManager = meta.manager;
            return null;
        }
        render(
            <ToastRegion>
                <CaptureManager />
            </ToastRegion>,
        );

        capturedManager!.add({ title: "Fired from outside React" });

        await waitFor(() => {
            expect(screen.getByText("Fired from outside React")).toBeInTheDocument();
        });
    });
});
