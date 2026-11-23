package tallyvane.stories.application

import tallyvane.stories.domain.Claim
import tallyvane.stories.domain.Place

/**
 * What the reader made of some words.
 */
public sealed interface Reading {
    /**
     * Claims the reader heard, the place it heard named, and a short title it suggests. The backend still
     * checks every claim against the words before it becomes a fact.
     */
    public class Heard(public val claims: List<Claim>, public val place: Place, public val title: String?) : Reading {
        override fun toString(): String = "Heard(${claims.size})"
    }

    /**
     * The model could not be asked, or answered something that is not a reading.
     */
    public class Unavailable(public val reason: String) : Reading {
        override fun toString(): String = "Unavailable($reason)"
    }

    /**
     * The person has spent today's allowance of model tokens.
     */
    public class OverBudget : Reading {
        override fun equals(other: Any?): Boolean = other is OverBudget

        override fun hashCode(): Int = OverBudget::class.hashCode()

        override fun toString(): String = "OverBudget"
    }
}
