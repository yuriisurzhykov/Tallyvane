const KEY = "tallyvane.recovery-notice";

/**
 * The fact that a recovery code opened this sign-in, kept in this tab until the person has read the notice.
 * The server forgets it the moment the code is accepted (the attempt is then simply complete), so without this
 * a reload before the notice was read would send the person on without ever telling them the authenticator
 * stopped working. It holds only how many codes are left, nothing secret.
 */
export class RecoveryNotice {
    private readonly storage: Storage | undefined;

    public constructor(storage: Storage | undefined = RecoveryNotice.available()) {
        this.storage = storage;
    }

    public remember(codesLeft: number): void {
        this.storage?.setItem(KEY, String(codesLeft));
    }

    /** How many recovery codes were left, while the notice is still unread; `undefined` when there is none to show. */
    public pending(): number | undefined {
        const text = this.storage?.getItem(KEY);
        return text !== null && text !== undefined && /^\d+$/.test(text) ? Number(text) : undefined;
    }

    public read(): void {
        this.storage?.removeItem(KEY);
    }

    private static available(): Storage | undefined {
        try {
            return window.sessionStorage;
        } catch {
            return undefined;
        }
    }
}
