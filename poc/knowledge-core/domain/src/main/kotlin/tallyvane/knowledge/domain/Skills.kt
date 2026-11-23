package tallyvane.knowledge.domain

/** A skill from the seed list. The list is fixed: nothing a model says can add to it. */
class Skill(private val slug: SkillSlug, private val name: String, private val aliases: List<String>) {
    fun isSlug(other: SkillSlug): Boolean = slug == other

    /** What the skill is called, for the prompts and the page. The slug is its identity. */
    fun slug(): SkillSlug = slug

    fun name(): String = name

    /** Whether [technology] is this skill itself, by its slug, name or an alias: Redis is `redis`. */
    fun isTheSameAs(technology: Technology): Boolean =
        technology.isNamed(slug.value) || technology.isNamed(name) || aliases.any { technology.isNamed(it) }

    fun tagWith(weight: Double): Tag = Tag(slug, weight)

    fun writeTo(record: Record) = record.skill(slug, name, aliases)

    interface Record {
        fun skill(slug: SkillSlug, name: String, aliases: List<String>)
    }
}

/** How much an action is evidence of a skill, from 0 to 1. */
class Tag(private val skill: SkillSlug, private val weight: Double) {
    init {
        require(weight in 0.0..1.0) { "A weight is between 0 and 1, not $weight." }
    }

    fun isFor(other: SkillSlug): Boolean = skill == other

    fun isForSkillOf(other: Tag): Boolean = skill == other.skill

    /** The stronger of two tags of one skill. */
    fun strongerWith(other: Tag): Tag {
        require(other.skill == skill) { "Tags of different skills do not merge." }
        return if (other.weight > weight) other else this
    }

    fun writeTo(record: Record) = record.tag(skill, weight)

    interface Record {
        fun tag(skill: SkillSlug, weight: Double)
    }
}

/** A skill that lies near an action in meaning, and how near. */
class SkillMatch(private val skill: SkillSlug, private val similarity: Double) {
    fun slug(): SkillSlug = skill

    fun reaches(threshold: Double): Boolean = similarity >= threshold

    fun isNearerThan(other: SkillMatch): Boolean = similarity > other.similarity

    fun asTag(): Tag = Tag(skill, similarity.coerceIn(0.0, 1.0))

    fun writeTo(record: Record) = record.match(skill, similarity)

    interface Record {
        fun match(skill: SkillSlug, similarity: Double)
    }
}

/**
 * Which skills an action is evidence of: the skills of the technologies it used, at full weight, and then
 * the nearest [limit] skills in meaning that are near enough, weighted by how near.
 */
class SkillTags(private val threshold: Double, private val limit: Int) {
    fun pick(nearest: List<SkillMatch>, catalog: List<Skill>, technologies: List<Technology>): List<Tag> {
        val exact = catalog.filter { skill -> technologies.any { skill.isTheSameAs(it) } }.map { it.tagWith(1.0) }
        val semantic =
            nearest
                .filter { it.reaches(threshold) }
                .sortedWith { a, b -> if (a.isNearerThan(b)) -1 else if (b.isNearerThan(a)) 1 else 0 }
                .take(limit)
                .map { it.asTag() }
        val picked = mutableListOf<Tag>()
        for (tag in exact + semantic) {
            val sameSkill = picked.indexOfFirst { existing -> existing.isForSkillOf(tag) }
            if (sameSkill < 0) picked += tag else picked[sameSkill] = picked[sameSkill].strongerWith(tag)
        }
        return picked
    }
}

/** When two actions are alike enough to ask the person whether they are one. */
class DuplicateRule(private val threshold: Double) {
    fun suspects(similarity: Double): Boolean = similarity >= threshold
}
