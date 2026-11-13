package tallyvane.stories.domain

/**
 * What a story has to say before it is strong, in the order it is asked for.
 */
public enum class Slot {
    /**
     * What the system was.
     */
    System,

    /**
     * What the person did themselves, not the team.
     */
    Role,

    /**
     * How it ended.
     */
    Outcome,

    /**
     * How big it was.
     */
    Scale,
}
