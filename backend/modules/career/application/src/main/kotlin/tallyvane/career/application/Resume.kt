package tallyvane.career.application

/**
 * A résumé: a selection of the person's own facts, each traceable to their words (law 2), 0 tokens.
 */
public class Resume(public val roles: List<Role>, public val skills: List<String>) {
    override fun toString(): String = "Resume(${roles.size})"

    /**
     * One place of work, with the bullets chosen for it.
     */
    public class Role(
        public val company: String?,
        public val position: String?,
        public val period: String?,
        public val bullets: List<String>,
    ) {
        override fun toString(): String = "Role(${bullets.size})"
    }
}
