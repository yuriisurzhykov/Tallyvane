const CHANNEL = "tallyvane-sign-in";

/**
 * How the sign-in window tells the page that waits for it. Google's pages cut a window off from the one
 * that opened it, so the two cannot talk directly; a `BroadcastChannel` reaches every tab of this site.
 */
export class SignInSignal {
    public announce(): void {
        const channel = new BroadcastChannel(CHANNEL);
        channel.postMessage("signed-in");
        channel.close();
    }

    /** Calls `onSignedIn` for every announcement until the returned function is called. */
    public listen(onSignedIn: () => void): () => void {
        const channel = new BroadcastChannel(CHANNEL);
        channel.onmessage = onSignedIn;
        return () => {
            channel.close();
        };
    }
}
