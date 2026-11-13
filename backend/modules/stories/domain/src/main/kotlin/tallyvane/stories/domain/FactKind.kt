package tallyvane.stories.domain

/**
 * What a fact says about a piece of work.
 */
public enum class FactKind {
    /**
     * What the system or the problem was.
     */
    Context,

    /**
     * Something that was done.
     */
    Action,

    /**
     * A choice that was made, and so somebody's to make.
     */
    Decision,

    /**
     * How it ended.
     */
    Outcome,

    /**
     * How big it was: people, requests, money.
     */
    Scale,

    /**
     * Who it was done with or for.
     */
    People,

    /**
     * What was learnt from it.
     */
    Learning,
}
