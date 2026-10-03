/** What a page does with each way a code can be answered. Exactly one of these is called. */
export interface CodeReaction<T> {
    /** The code was right. `codesLeft` is told for a recovery code only. */
    accepted(codesLeft: number | undefined): T;
    /** The code was wrong; `pauseSeconds` is how long the next one will not be looked at, `0` when there is no pause yet. */
    wrong(pauseSeconds: number): T;
    /** A pause is running and the code was not even looked at. */
    paused(pauseSeconds: number): T;
    /** The attempt is over, or there is none: the person begins again. */
    over(): T;
    /** The attempt changed under the request, or wants nothing more of this kind: ask where it stands. */
    changed(): T;
}

type Outcome =
    | { readonly kind: "accepted"; readonly codesLeft: number | undefined }
    | { readonly kind: "wrong"; readonly pauseSeconds: number }
    | { readonly kind: "paused"; readonly pauseSeconds: number }
    | { readonly kind: "over" }
    | { readonly kind: "changed" };

/** How the server answered a code, as something a page asks what to do about. */
export class CodeAnswer {
    private readonly outcome: Outcome;

    private constructor(outcome: Outcome) {
        this.outcome = outcome;
    }

    public static accepted(codesLeft: number | undefined): CodeAnswer {
        return new CodeAnswer({ kind: "accepted", codesLeft });
    }

    public static wrong(pauseSeconds: number): CodeAnswer {
        return new CodeAnswer({ kind: "wrong", pauseSeconds });
    }

    public static paused(pauseSeconds: number): CodeAnswer {
        return new CodeAnswer({ kind: "paused", pauseSeconds });
    }

    public static over(): CodeAnswer {
        return new CodeAnswer({ kind: "over" });
    }

    public static changed(): CodeAnswer {
        return new CodeAnswer({ kind: "changed" });
    }

    public when<T>(reaction: CodeReaction<T>): T {
        const outcome = this.outcome;
        switch (outcome.kind) {
            case "accepted":
                return reaction.accepted(outcome.codesLeft);
            case "wrong":
                return reaction.wrong(outcome.pauseSeconds);
            case "paused":
                return reaction.paused(outcome.pauseSeconds);
            case "over":
                return reaction.over();
            case "changed":
                return reaction.changed();
        }
    }
}
