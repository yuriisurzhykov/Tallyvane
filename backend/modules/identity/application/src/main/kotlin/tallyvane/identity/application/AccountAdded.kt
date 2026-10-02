package tallyvane.identity.application

/**
 * How [tallyvane.identity.application.port.KeptAccounts.add] ended. An `enum` rather than a pair of
 * classes because neither case carries anything, and `port-is-interface` keeps everything but
 * interfaces out of the `port` package.
 */
public enum class AccountAdded {
    /**
     * The account is kept.
     */
    Added,

    /**
     * An account for the same Google subject was already kept; this one was not.
     */
    SubjectTaken,
}
