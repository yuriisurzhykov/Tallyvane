package tallyvane.knowledge.domain

import java.util.Locale

/** A technology as the graph names it: the canonical spelling when it is a known one. */
class Technology(private val name: String) {
    init {
        require(name.isNotBlank()) { "A technology has a name." }
    }

    fun isNamed(other: String): Boolean = name.equals(other, ignoreCase = true)

    fun writeTo(record: Record) = record.technology(name)

    override fun equals(other: Any?): Boolean = other is Technology && other.name.equals(name, ignoreCase = true)

    override fun hashCode(): Int = name.lowercase(Locale.ROOT).hashCode()

    override fun toString(): String = name

    interface Record {
        fun technology(name: String)
    }
}

/**
 * The technologies the product knows by name, each with the other ways people write it, Cyrillic ones
 * included: `редис` is Redis. A name it does not know is kept as the person wrote it, not guessed at.
 */
class TechnologyDictionary(entries: List<Entry>) {
    class Entry(val name: String, val aliases: List<String>)

    private val canonical: Map<String, String> =
        entries
            .flatMap { entry -> (entry.aliases + entry.name).map { key(it) to entry.name } }
            .toMap()

    fun resolve(raw: String): Technology = Technology(canonical[key(raw)] ?: raw.trim())

    /** Whether [word] is the name or an alias of a technology in the dictionary. */
    fun recognises(word: String): Boolean = canonical.containsKey(key(word))

    private fun key(raw: String): String = raw.trim().lowercase(Locale.ROOT)
}

/** A person's colleagues are kept by the role they have, in lower case: "DBA" and "dba" are one. */
class Collaborator(role: String) {
    private val role: String = role.trim().lowercase(Locale.ROOT)

    init {
        require(this.role.isNotBlank()) { "A collaborator has a role." }
    }

    fun isInRole(other: String): Boolean = role == other.trim().lowercase(Locale.ROOT)

    fun writeTo(record: Record) = record.collaborator(role)

    override fun equals(other: Any?): Boolean = other is Collaborator && other.role == role

    override fun hashCode(): Int = role.hashCode()

    override fun toString(): String = role

    interface Record {
        fun collaborator(role: String)
    }
}

/** A company's name, and the form of it two spellings of the same company share. */
class CompanyName(private val name: String) {
    init {
        require(name.isNotBlank()) { "A company has a name." }
    }

    fun writeTo(record: Record) =
        record.company(name.trim(), name.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " "))

    interface Record {
        fun company(name: String, normalised: String)
    }
}
