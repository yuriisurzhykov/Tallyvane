import type { components } from "frontend-shared/api";
import { CodeOffer } from "./CodeOffer";

type Wire = components["schemas"]["SignInState"];

/**
 * What a page does with each state of a sign-in. Exactly one of these is called, so a page has to say
 * what it does in every case and cannot forget one when the server learns a new state.
 */
export interface SignInRoute<T> {
    /** A code is asked for (`awaiting`), or would be if the pause were over (`paused`). */
    answer(offer: CodeOffer): T;
    /** Everything wanted is proved: the sign-in can be redeemed, or the confirmation taken. */
    open(): T;
    /** The attempt is over; the person begins again. */
    startAgain(): T;
    /** Everything is proved, but the person may not go in yet (`restricted`), which nothing in this slice can lift. */
    blocked(): T;
}

/** Where a sign-in or a confirmation stands, as `GET /sign-in` tells it. */
export class SignInState {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    public proceed<T>(route: SignInRoute<T>): T {
        switch (this.wire.state) {
            case "awaiting":
            case "paused": {
                const offer = new CodeOffer(this.wire.factors, this.wire.retry_after ?? 0);
                return offer.isEmpty() ? route.startAgain() : route.answer(offer);
            }
            case "complete":
                return route.open();
            case "restricted":
                return route.blocked();
            case "exhausted":
            case "expired":
                return route.startAgain();
        }
    }
}
