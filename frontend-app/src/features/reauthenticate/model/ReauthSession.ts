import type { ExpiredSessionHandler } from "frontend-shared/api";

/**
 * "The session ended and someone has to sign in again" as a thing the page can watch. The transport asks
 * (`reauthenticate`) and waits; the dialog shows while this is waiting; when sign-in is confirmed the
 * waiting requests go on. A subscribable store, so React can read it with `useSyncExternalStore`.
 */
export class ReauthSession implements ExpiredSessionHandler {
    private pending: Promise<void> | undefined;
    private release: (() => void) | undefined;
    private readonly listeners = new Set<() => void>();

    public reauthenticate(): Promise<void> {
        if (this.pending === undefined) {
            this.pending = new Promise<void>((resolve) => {
                this.release = resolve;
            });
            this.notify();
        }
        return this.pending;
    }

    public isWaiting = (): boolean => this.pending !== undefined;

    public confirm(): void {
        const release = this.release;
        this.pending = undefined;
        this.release = undefined;
        release?.();
        this.notify();
    }

    public subscribe = (listener: () => void): (() => void) => {
        this.listeners.add(listener);
        return () => {
            this.listeners.delete(listener);
        };
    };

    private notify(): void {
        this.listeners.forEach((listener) => {
            listener();
        });
    }
}
