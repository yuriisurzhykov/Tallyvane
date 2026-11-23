package tallyvane.platform.llm

/**
 * What the model already answered, so the same question is paid for once.
 *
 * Each call is a transaction of its own.
 */
public interface Memo {
    /**
     * The text kept under [key], or null.
     */
    public suspend fun recall(key: String): String?

    /**
     * Keeps [text] under [key]. Keeping the same key twice changes nothing.
     */
    public suspend fun keep(key: String, spec: String, model: String, text: String, tokens: TokenCount)
}
