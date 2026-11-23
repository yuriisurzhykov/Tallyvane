package tallyvane.career.application

import tallyvane.catalog.contract.SkillCatalog
import tallyvane.platform.kernel.UseCase
import tallyvane.stories.contract.Experience
import kotlin.uuid.Uuid

/**
 * Builds a résumé by selection, not by writing: only facts the person said they did themselves, results first.
 * A fact the team did is never presented as theirs.
 */
public interface ShowResumeUseCase : UseCase {
    public suspend fun show(account: Uuid): Resume

    public class ShowResume(private val experience: Experience, private val catalog: SkillCatalog) :
        ShowResumeUseCase {
        override suspend fun show(account: Uuid): Resume {
            val stories = experience.of(account)
            val roles = stories.groupBy { Triple(it.company, it.position, it.period) }.map { (place, told) ->
                val bullets = told.flatMap { story -> story.facts.filter { it.mine && it.kind in SHOWN } }
                    .sortedBy { SHOWN.indexOf(it.kind) }
                    .map { it.text }
                    .distinct()
                    .take(BULLETS)
                Resume.Role(place.first, place.second, place.third, bullets)
            }.filter { it.bullets.isNotEmpty() }
            val standing = Standing(stories)
            val skills = catalog.skills().filter { standing.of(it.slug) != Level.Gap }.map { it.name }
            return Resume(roles, skills)
        }

        override fun toString(): String = "ShowResume(experience=$experience)"

        private companion object {
            const val BULLETS = 5
            val SHOWN = listOf("outcome", "decision", "action", "scale")
        }
    }
}
