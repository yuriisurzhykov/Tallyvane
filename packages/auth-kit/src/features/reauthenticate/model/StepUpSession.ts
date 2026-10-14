import { StepUpDeclined, type StepUpHandler } from "frontend-shared/api";

/**
 * "A dangerous act needs a recent proof and someone has to give it" as a thing the page can watch. The
 * transport asks (`confirm`) and waits; the dialog shows while this is waiting; when the confirmation
 * window reports back the waiting requests go on, and when the person gives up they fail as they would
 * have. A subscribable store, so React can read it with `useSyncExternalStore`.
 */
export class StepUpSession implements StepUpHandler {
    private pending: Promise<void> | undefined;
    private release: { readonly resolve: () => void; readonly reject: (reason: Error) => void } | undefined;
    private readonly listeners = new Set<() => void>();

    public confirm(): Promise<void> {
        if (this.pending === undefined) {
            this.pending = new Promise<void>((resolve, reject) => {
                this.release = { resolve, reject };
            });
            this.notify();
        }
        return this.pending;
    }

    public isWaiting = (): boolean => this.pending !== undefined;

    /** The person proved who they are: the requests that waited go on. */
    public done(): void {
        const release = this.release;
        this.settle();
        release?.resolve();
    }

    /** The person would rather not: the requests that waited fail. */
    public cancel(): void {
        const release = this.release;
        this.settle();
        release?.reject(new StepUpDeclined());
    }

    public subscribe = (listener: () => void): (() => void) => {
        this.listeners.add(listener);
        return () => {
            this.listeners.delete(listener);
        };
    };

    private settle(): void {
        this.pending = undefined;
        this.release = undefined;
        this.notify();
    }

    private notify(): void {
        this.listeners.forEach((listener) => {
            listener();
        });
    }
}
