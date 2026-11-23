package tallyvane.platform.llm

/**
 * A [Memo] in a map (ADR-044).
 */
class MemoFake : Memo {
    private val kept = mutableMapOf<String, String>()

    val size: Int get() = kept.size

    override suspend fun recall(key: String): String? = kept[key]

    override suspend fun keep(key: String, spec: String, model: String, text: String, tokens: TokenCount) {
        kept.putIfAbsent(key, text)
    }
}
