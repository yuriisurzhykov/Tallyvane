package tallyvane.gradle.graph

internal class Finding(
    private val text: String,
) {
    override fun toString(): String = text

    override fun equals(other: Any?): Boolean = other is Finding && other.text == text

    override fun hashCode(): Int = text.hashCode()
}
