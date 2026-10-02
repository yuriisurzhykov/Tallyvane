package tallyvane.authentication.application

/**
 * What a return from Google found before anything was traded with Google.
 */
internal sealed interface Arrival {
    /**
     * Everything is in order: trade [code] for the identity Google vouches for.
     */
    class Ready(val trip: Trip, val code: String) : Arrival

    /**
     * The sign-in cannot go on, for [reason]. The attempt is forgotten in the same transaction.
     */
    class Stopped(val reason: TurnBack) : Arrival
}
