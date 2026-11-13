package tallyvane.career.application

import tallyvane.platform.kernel.Failure

public sealed interface GoalOutcome {
    public class Set internal constructor(public val role: String) : GoalOutcome {
        override fun equals(other: Any?): Boolean = other is Set && other.role == role

        override fun hashCode(): Int = role.hashCode()

        override fun toString(): String = "Set($role)"
    }

    public sealed interface Failed :
        GoalOutcome,
        Failure {
        /**
         * The catalog has no such role track.
         */
        public class UnknownRole internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is UnknownRole

            override fun hashCode(): Int = UnknownRole::class.hashCode()

            override fun toString(): String = "UnknownRole"
        }
    }
}
