package tallyvane.stories.domain

/**
 * What a reader says a person said. Nothing in it is believed until a [Hearing] finds its fragments in the
 * words: a claim is the model's, a fact is the person's.
 *
 * @property fragments The parts of the words the claim rests on, as the reader copied them.
 * @property mine Whether the person did it themselves, not their team.
 */
public class Claim(
    public val kind: FactKind,
    public val text: String,
    public val fragments: List<String>,
    public val figures: List<Double>,
    public val technologies: List<String>,
    public val skills: List<String>,
    public val mine: Boolean,
) {
    override fun toString(): String = "Claim($kind)"
}
