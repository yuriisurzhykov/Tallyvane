package tallyvane.knowledge.domain

/** The numbers written in a piece of text, as the checks of the quote and of a bullet read them. */
internal object Numbers {
    private val token = Regex("""(?<![\p{L}\p{N}.,])\d+(?:[.,]\d+)?""")

    fun written(text: String): List<Double> = token.findAll(text).map { it.value.replace(',', '.').toDouble() }.toList()
}
