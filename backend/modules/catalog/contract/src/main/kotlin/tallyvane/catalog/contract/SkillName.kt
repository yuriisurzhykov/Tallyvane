package tallyvane.catalog.contract

/**
 * A skill the product knows: the word other modules store ([slug]) and the words a person reads ([name]).
 */
public class SkillName(public val slug: String, public val name: String) {
    override fun equals(other: Any?): Boolean = other is SkillName && other.slug == slug && other.name == name

    override fun hashCode(): Int = listOf(slug, name).hashCode()

    override fun toString(): String = "SkillName($slug)"
}
