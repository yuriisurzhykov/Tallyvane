export interface AuthorWords {
    readonly you: string;
    /** For a version, or an activation, that a migration made and no account did. */
    readonly migration: string;
    readonly account: (shortId: string) => string;
}

/** Whoever is looking at the screen: the one version author the person recognises on sight. */
export interface Onlooker {
    is(accountId: string): boolean;
}

const SHORT_ID_LENGTH = 8;

/** Who did something to a version, when, and why: the same three facts for making one and for putting one in force. */
export class VersionStamp {
    private readonly by: string | undefined;
    private readonly at: string;
    private readonly why: string | undefined;

    public constructor(by: string | undefined, at: string, why: string | undefined) {
        this.by = by;
        this.at = at;
        this.why = why;
    }

    /** "You" for the person looking, a short form of the account otherwise (the server gives no names), and "migration" when no account did it. */
    public author(words: AuthorWords, onlooker: Onlooker): string {
        if (this.by === undefined) {
            return words.migration;
        }
        return onlooker.is(this.by) ? words.you : words.account(this.by.slice(0, SHORT_ID_LENGTH));
    }

    public when(locale: string): string {
        return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(this.at));
    }

    /** The reason that was given, or `undefined` for something a migration did. */
    public reason(): string | undefined {
        return this.why;
    }
}
