package tallyvane.stories.domain

/**
 * How far a story has got.
 */
public enum class StoryState {
    /**
     * Something is still to be asked.
     */
    Open,

    /**
     * It says what the person did and how it ended: résumé lines are written from it.
     */
    Strong,

    /**
     * The person had nothing more to add before it became strong. Its facts still count as evidence.
     */
    Closed,
}
