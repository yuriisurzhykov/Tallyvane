package tallyvane.platform.llm

/**
 * How much model a call deserves. A module says which, and the composition root says which model that is.
 */
public enum class ModelTier {
    /**
     * Reading and sorting: extraction, classification.
     */
    Fast,

    /**
     * Writing that a person will send out under their own name.
     */
    Careful,
}
