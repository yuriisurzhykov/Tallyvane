package tallyvane.platform.llm

/**
 * What a call cost: tokens sent, tokens written back, and how many of the sent ones were read from the
 * provider's prompt cache.
 */
public class TokenCount(public val input: Long, public val output: Long, public val cached: Long) {
    init {
        require(input >= 0 && output >= 0 && cached >= 0) { "A count of tokens is not negative." }
    }

    /**
     * What a budget counts.
     */
    public fun total(): Long = input + output

    override fun equals(other: Any?): Boolean =
        other is TokenCount && other.input == input && other.output == output && other.cached == cached

    override fun hashCode(): Int = listOf(input, output, cached).hashCode()

    override fun toString(): String = "TokenCount(in=$input, out=$output, cached=$cached)"
}
