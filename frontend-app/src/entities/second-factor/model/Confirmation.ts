import type { RecoveryCodes } from "./RecoveryCodes";

/** What a page does with each way the first code can be answered. Exactly one of these is called. */
export interface ConfirmationReaction<T> {
    /** The code was right: TOTP is on, and these codes are told once. */
    confirmed(codes: RecoveryCodes): T;
    /** The code was wrong; the set-up stays open and no limit applies. */
    wrong(): T;
    /** Nothing was begun, or TOTP is already on: the standing has to be read again to tell which. */
    conflict(): T;
}

type Outcome =
    | { readonly kind: "confirmed"; readonly codes: RecoveryCodes }
    | { readonly kind: "wrong" }
    | { readonly kind: "conflict" };

/** How the server answered the first code, as something a page asks what to do about. */
export class Confirmation {
    private readonly outcome: Outcome;

    private constructor(outcome: Outcome) {
        this.outcome = outcome;
    }

    public static confirmed(codes: RecoveryCodes): Confirmation {
        return new Confirmation({ kind: "confirmed", codes });
    }

    public static wrong(): Confirmation {
        return new Confirmation({ kind: "wrong" });
    }

    public static conflict(): Confirmation {
        return new Confirmation({ kind: "conflict" });
    }

    public when<T>(reaction: ConfirmationReaction<T>): T {
        const outcome = this.outcome;
        switch (outcome.kind) {
            case "confirmed":
                return reaction.confirmed(outcome.codes);
            case "wrong":
                return reaction.wrong();
            case "conflict":
                return reaction.conflict();
        }
    }
}
