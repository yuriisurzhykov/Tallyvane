package tallyvane.identity.application.session

/**
 * What came of a request to revoke one session.
 */
public sealed interface RevokeSessionOutcome {
    public data object Revoked : RevokeSessionOutcome

    /**
     * Covers both "no session has this id" and "a session has it, but belongs to a different
     * account" — the same "don't leak why" choice
     * [tallyvane.identity.application.password.SignInWithPasswordUseCase] already makes for a
     * password: telling the caller which one it was would confirm that a guessed session id
     * belongs to someone else's account.
     */
    public data object NotFound : RevokeSessionOutcome
}
