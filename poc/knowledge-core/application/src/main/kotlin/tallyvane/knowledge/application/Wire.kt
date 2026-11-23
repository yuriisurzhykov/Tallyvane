package tallyvane.knowledge.application

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What a model's reply was, and was not: either a reply a step can use or one it asks the model to redo. */
class InvalidLlmOutput(message: String) : Exception(message)

/** A step that needs the model gave up after the model's reply was rejected twice. */
class LlmStepFailed(val step: String, val reason: String) : Exception("The $step step failed: $reason")

/** An answer that cannot be put in the graph for a reason the person can fix. */
class AnswerNotUsable(message: String) : Exception(message)

internal object Wire {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** The JSON object in [reply], which models often wrap in a code fence or a sentence. */
    fun objectIn(reply: String): String {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end < start) throw InvalidLlmOutput("The reply has no JSON object.")
        return reply.substring(start, end + 1)
    }

    inline fun <reified T> decode(reply: String): T =
        try {
            json.decodeFromString<T>(objectIn(reply))
        } catch (e: SerializationException) {
            throw InvalidLlmOutput("The reply is not the JSON that was asked for: ${e.message?.lineSequence()?.first()}")
        } catch (e: IllegalArgumentException) {
            throw InvalidLlmOutput("The reply is not the JSON that was asked for: ${e.message?.lineSequence()?.first()}")
        }
}

/** `{"n": "...", "q": "..."}`: a name and the words it was read from. */
@Serializable
internal class NamedWire(val n: String = "", val q: String = "")

@Serializable
internal class RoleWire(
    val title: String? = null,
    val start: String? = null,
    val end: String? = null,
    val current: Boolean? = null,
    val q: String = "",
)

@Serializable
internal class ProjectWire(val title: String? = null, val problem: String? = null, val q: String = "")

@Serializable
internal class ActionWire(
    val kind: String = "",
    val verb: String = "",
    val what: String = "",
    val finding: String? = null,
    val techs: List<NamedWire> = emptyList(),
    val collabs: List<NamedWire> = emptyList(),
    val q: String = "",
)

@Serializable
internal class OutcomeWire(
    val what: String = "",
    val dir: String? = null,
    @SerialName("val") val value: Double? = null,
    val unit: String? = null,
    val approx: Boolean = false,
    val of: List<Int> = emptyList(),
    val q: String = "",
)

@Serializable
internal class ExtractionWire(
    val company: NamedWire? = null,
    val role: RoleWire? = null,
    val project: ProjectWire? = null,
    val actions: List<ActionWire> = emptyList(),
    val outcomes: List<OutcomeWire> = emptyList(),
)

@Serializable
internal class BulletWire(val text: String = "", val a: List<String> = emptyList())

@Serializable
internal class BulletsWire(val bullets: List<BulletWire> = emptyList())

@Serializable
internal class TagsWire(val skills: List<String> = emptyList())

@Serializable
internal class RequirementWire(val skill: String = "", val importance: Int = 1, val must: Boolean = false)

@Serializable
internal class VacancyWire(
    val title: String = "",
    val reqs: List<RequirementWire> = emptyList(),
    val unmapped: List<String> = emptyList(),
)
