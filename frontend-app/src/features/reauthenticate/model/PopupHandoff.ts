import { SignInSignal } from "./SignInSignal";

const KEY = "tallyvane.reauth-pending";
const FRESH_FOR_MS = 10 * 60 * 1000;

/**
 * The page that waits leaves a note before sending anyone to Google; the sign-in window finds it on
 * return and knows its job is to report back and close, not to carry on into the console. A note older
 * than ten minutes is forgotten, so an abandoned attempt cannot hijack a later ordinary sign-in.
 */
export class PopupHandoff {
    private readonly signal = new SignInSignal();

    public expect(): void {
        try {
            window.localStorage.setItem(KEY, String(Date.now()));
        } catch {
            // Without storage the window simply carries on into the console; the page behind still works.
        }
    }

    public forget(): void {
        try {
            window.localStorage.removeItem(KEY);
        } catch {
            // Nothing was stored, so there is nothing to forget.
        }
    }

    /** `true` when this window was a re-sign-in: the page behind has been told, and the window should close. */
    public completeIfExpected(): boolean {
        if (!this.isExpected()) {
            return false;
        }
        this.forget();
        this.signal.announce();
        return true;
    }

    private isExpected(): boolean {
        try {
            const since = Number(window.localStorage.getItem(KEY));
            return Number.isFinite(since) && since > 0 && Date.now() - since < FRESH_FOR_MS;
        } catch {
            return false;
        }
    }
}
