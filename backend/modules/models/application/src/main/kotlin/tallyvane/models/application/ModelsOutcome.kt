package tallyvane.models.application

import tallyvane.platform.kernel.Failure

/**
 * What an administrator's look at, or change to, the model providers ended in.
 */
public sealed interface ModelsOutcome {
    public class Shown internal constructor(public val providers: List<ProviderSetting>, public val costs: List<Cost>) :
        ModelsOutcome {
        override fun toString(): String = "Shown(${providers.size}, ${costs.size})"
    }

    public sealed interface Failed :
        ModelsOutcome,
        Failure {
        public class NotAnAdministrator internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NotAnAdministrator

            override fun hashCode(): Int = NotAnAdministrator::class.hashCode()

            override fun toString(): String = "NotAnAdministrator"
        }

        public class Invalid internal constructor(public val reason: String) : Failed {
            override fun equals(other: Any?): Boolean = other is Invalid && other.reason == reason

            override fun hashCode(): Int = reason.hashCode()

            override fun toString(): String = "Invalid($reason)"
        }

        public class Missing internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Missing

            override fun hashCode(): Int = Missing::class.hashCode()

            override fun toString(): String = "Missing"
        }

        public class NameTaken internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NameTaken

            override fun hashCode(): Int = NameTaken::class.hashCode()

            override fun toString(): String = "NameTaken"
        }
    }
}
