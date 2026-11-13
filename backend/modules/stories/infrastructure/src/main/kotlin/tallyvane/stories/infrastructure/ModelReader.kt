package tallyvane.stories.infrastructure

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.llm.ModelCall
import tallyvane.platform.llm.ModelReply
import tallyvane.platform.llm.ModelTier
import tallyvane.platform.llm.core.Model
import tallyvane.stories.application.Reading
import tallyvane.stories.application.port.Reader
import tallyvane.stories.domain.Fact
import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Place
import tallyvane.stories.domain.Slot
import kotlin.uuid.Uuid

/**
 * [Reader] over the model, by specs `extract_facts@1` and `correct_fact@1`. An answer that is not the JSON the
 * spec asks for is unavailable, not a reason to guess.
 */
internal class ModelReader(private val model: Model, private val skills: List<String>) : Reader {
    private val json = Json { ignoreUnknownKeys = true }
    private val extract = prompt("extract_facts")
    private val correction = prompt("correct_fact")

    override suspend fun read(words: UntrustedText, account: Uuid, asked: Slot?): Reading {
        val input = ModelInput(words.cleaned(), skills, asked?.name?.lowercase())
        return asked(EXTRACT, extract, input, account) { text ->
            val answer = json.decodeFromString<ModelAnswer>(text)
            val place = answer.place
            Reading.Heard(
                answer.facts.mapNotNull { it.claim() },
                Place(place.company, place.position, place.started, place.ended),
                place.title,
            )
        }
    }

    override suspend fun correct(fact: Fact, words: UntrustedText, account: Uuid): Reading {
        val old = OldFact().also { fact.writeTo(it) }.fact()
        val input = ModelInput(words.cleaned(), skills, null, old)
        return asked(CORRECT, correction, input, account) { text ->
            Reading.Heard(listOfNotNull(json.decodeFromString<ModelFact>(text).claim()), NOWHERE, null)
        }
    }

    private suspend fun asked(
        spec: String,
        instructions: String,
        input: ModelInput,
        account: Uuid,
        read: (String) -> Reading,
    ): Reading = when (
        val reply = model.ask(
            ModelCall(spec, ModelTier.Fast, instructions, json.encodeToString(input), account),
        )
    ) {
        is ModelReply.Answered -> try {
            read(reply.text.substringAfter("```json").substringBeforeLast("```").trim())
        } catch (unreadable: SerializationException) {
            Reading.Unavailable("The model answered $spec with something else: ${unreadable.message?.take(LOGGED)}")
        } catch (unreadable: IllegalArgumentException) {
            Reading.Unavailable("The model answered $spec with something else: ${unreadable.message?.take(LOGGED)}")
        }
        is ModelReply.Unavailable -> Reading.Unavailable(reply.reason)
        is ModelReply.OverBudget -> Reading.OverBudget()
    }

    private fun prompt(name: String): String =
        checkNotNull(ModelReader::class.java.getResource("/prompts/$name.txt")) { "prompt $name is missing" }.readText()

    override fun toString(): String = "ModelReader($model)"

    private class OldFact : Fact.Record {
        private var fact: ModelFact? = null
        private val quotes = mutableListOf<String>()

        fun fact(): ModelFact? = fact?.let {
            ModelFact(it.kind, it.text, quotes.toList(), it.figures, it.tech, it.skills, it.mine)
        }

        @Suppress("LongParameterList") // What a fact is.
        override fun fact(
            id: Uuid,
            kind: FactKind,
            text: String,
            figures: List<Double>,
            technologies: List<String>,
            skills: List<String>,
            mine: Boolean,
            replacedBy: Uuid?,
        ) {
            fact = ModelFact(kind.name.lowercase(), text, emptyList(), figures, technologies, skills, mine)
        }

        override fun quote(fact: Uuid, utterance: Uuid, fragment: String) {
            quotes += fragment
        }
    }

    private companion object {
        const val EXTRACT = "extract_facts@1"
        const val CORRECT = "correct_fact@1"
        const val LOGGED = 200
        val NOWHERE = Place(null, null, null, null)
    }
}
