import type { SecurityEntry } from "./SecurityEntry";

/** The entries the screen has so far, newest first, and where to continue from when there are more. */
export class ActivityLog {
    private readonly entries: readonly SecurityEntry[];
    private readonly next: string | undefined;

    public constructor(entries: readonly SecurityEntry[], next: string | undefined) {
        this.entries = entries;
        this.next = next;
    }

    /** This log with the entries that follow it, and with the way on that the following page gave. */
    public andThen(page: ActivityLog): ActivityLog {
        return new ActivityLog([...this.entries, ...page.entries], page.next);
    }

    public isEmpty(): boolean {
        return this.entries.length === 0;
    }

    public hasMore(): boolean {
        return this.next !== undefined;
    }

    /** What the server wants back to continue after the last entry, or nothing at the end. Opaque to us. */
    public after(): string | undefined {
        return this.next;
    }

    /** A position is all an entry has to be told apart by: the log only grows at its end. */
    public map<Shown>(show: (entry: SecurityEntry, position: number) => Shown): Shown[] {
        return this.entries.map(show);
    }
}
