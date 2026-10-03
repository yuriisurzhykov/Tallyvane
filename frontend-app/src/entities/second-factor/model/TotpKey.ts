const GROUP = 4;

/** The key of a new authenticator set-up, told once: to type, to open as a link, and to scan. */
export class TotpKey {
    private readonly key: string;
    private readonly uri: string;

    public constructor(key: string, uri: string) {
        this.key = key;
        this.uri = uri;
    }

    /** The key in groups of four, which is how it is read and typed without losing the place. */
    public spaced(): string {
        const groups: string[] = [];
        for (let at = 0; at < this.key.length; at += GROUP) {
            groups.push(this.key.slice(at, at + GROUP));
        }
        return groups.join(" ");
    }

    /** The `otpauth://` address: a link on a phone, the content of the QR code on a computer. */
    public link(): string {
        return this.uri;
    }
}
