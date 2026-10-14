package tallyvane.authentication.application

/**
 * One thing wrong with what an administrator sent, said so that a screen can point at the field and explain it.
 *
 * @param field The name of the field as the client sent it, such as `max_failures` or `steps[1].accepts`.
 * @param code What is wrong, in a form a client can branch on, such as `out-of-bounds`.
 * @param said The same, in words, with the numbers the code sets: the screen shows it without knowing the bounds.
 */
public class Fault(private val field: String, private val code: String, private val said: String) {
    /**
     * Tells [record] the field, the code and the words.
     */
    public fun writeTo(record: Record) {
        record.fault(field, code, said)
    }

    override fun equals(other: Any?): Boolean =
        other is Fault && other.field == field && other.code == code && other.said == said

    override fun hashCode(): Int = 31 * (31 * field.hashCode() + code.hashCode()) + said.hashCode()

    override fun toString(): String = "Fault(field=$field, code=$code)"

    /**
     * What a fault tells whoever shows it.
     */
    public fun interface Record {
        public fun fault(field: String, code: String, said: String)
    }
}
