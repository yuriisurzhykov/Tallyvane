/**
 * Runs one device's renames one after another, in the order they were committed. Two requests in flight
 * have no order at the server, and the older could land last and put the old name back.
 */
export class RenameQueue {
    private tail: Promise<void> = Promise.resolve();
    private waiting = 0;

    /**
     * Runs [task] once every earlier one has finished, however they ended. Resolves with whether nothing is
     * waiting behind it, which is when the list is worth reading again; rejects with the task's own failure.
     */
    public async run(task: () => Promise<void>): Promise<boolean> {
        this.waiting += 1;
        const turn = this.tail.then(task);
        this.tail = turn.then(
            () => undefined,
            () => undefined,
        );
        try {
            await turn;
        } finally {
            this.waiting -= 1;
        }
        return this.waiting === 0;
    }
}
