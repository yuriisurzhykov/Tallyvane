/** Lets any number of callers share one run of a task: while it is running, a new call gets the same promise. */
export class SingleFlight {
    private pending: Promise<void> | undefined;

    public run(task: () => Promise<void>): Promise<void> {
        this.pending ??= task().finally(() => {
            this.pending = undefined;
        });
        return this.pending;
    }
}
