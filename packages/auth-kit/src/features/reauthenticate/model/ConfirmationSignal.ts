const CHANNEL = "tallyvane-step-up";

/**
 * How the confirmation window tells the page that waits for it, for the reason `SignInSignal` exists:
 * Google's pages cut a window off from the one that opened it, and a `BroadcastChannel` reaches every
 * tab of this site.
 */
export class ConfirmationSignal {
    public announce(): void {
        const channel = new BroadcastChannel(CHANNEL);
        channel.postMessage("confirmed");
        channel.close();
    }

    /** Calls `onConfirmed` for every announcement until the returned function is called. */
    public listen(onConfirmed: () => void): () => void {
        const channel = new BroadcastChannel(CHANNEL);
        channel.onmessage = onConfirmed;
        return () => {
            channel.close();
        };
    }
}
