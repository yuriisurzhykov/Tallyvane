import { isSafeRelativePath } from "frontend-shared/lib/safe-relative-path";

const KEY = "tallyvane.return-path";

/**
 * Where the person was going when the console sent them to sign in. Google's round trip loses the
 * address, so it waits here, in this tab, until sign-in is done. Anything that is not a path on this
 * site is dropped on the way in and on the way out.
 */
export class ReturnPath {
    private readonly storage: Storage | undefined;

    public constructor(storage: Storage | undefined = ReturnPath.available()) {
        this.storage = storage;
    }

    public remember(path: string | undefined): void {
        if (!isSafeRelativePath(path)) {
            return;
        }
        this.storage?.setItem(KEY, path);
    }

    public forget(): void {
        this.storage?.removeItem(KEY);
    }

    /** The remembered path, once; `undefined` when there is none worth following. */
    public take(): string | undefined {
        const path = this.storage?.getItem(KEY);
        this.storage?.removeItem(KEY);
        return isSafeRelativePath(path) ? path : undefined;
    }

    private static available(): Storage | undefined {
        try {
            return window.sessionStorage;
        } catch {
            return undefined;
        }
    }
}
