package tallyvane.career.application

import tallyvane.career.application.port.Goals
import tallyvane.catalog.contract.SkillCatalog
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.contract.Experience
import tallyvane.stories.contract.ToldStory
import kotlin.uuid.Uuid

/**
 * Chooses the one most valuable next step by rules, 0 tokens (law 3: the model translates, rules decide).
 *
 * The ladder: nothing told yet → tell the first story; no goal → choose one; a story with a slot missing →
 * finish it; otherwise → close the most important gap on the route. Never a vacancy (ADR-040).
 */
public interface ShowHomeUseCase : UseCase {
    public suspend fun show(account: Uuid): Home

    public class ShowHome(
        private val goals: Goals,
        private val experience: Experience,
        private val catalog: SkillCatalog,
        private val transactions: TransactionRunner,
    ) : ShowHomeUseCase {
        override suspend fun show(account: Uuid): Home {
            val stories = experience.of(account)
            val role = transactions.inTransaction { Verdict.Commit(goals.of(account)) }?.let(catalog::role)
            val standing = Standing(stories)
            val names = catalog.skills().associate { it.slug to it.name }
            val route = role?.stations.orEmpty().map {
                Home.Stop(
                    it.skill,
                    names[it.skill] ?: it.skill,
                    it.importance,
                    standing.of(it.skill),
                    standing.evidence(it.skill),
                )
            }
            val (step, subject, detail) = step(stories, role?.slug, route)
            val roles = catalog.roles().map { it.slug to it.name }
            return Home(step, subject, detail, role?.slug, role?.name, route, stories.size, roles)
        }

        private fun step(
            stories: List<ToldStory>,
            role: String?,
            route: List<Home.Stop>,
        ): Triple<String, String?, String?> {
            val open = stories.firstOrNull { it.missing != null && it.facts.isNotEmpty() }
            val gap = route.filter { it.level != Level.Confirmed }.maxByOrNull { it.importance * weight(it.level) }
            return when {
                stories.isEmpty() -> Triple("tell_first", null, null)
                role == null -> Triple("set_goal", null, null)
                open != null -> Triple("finish_story", open.id.toString(), open.missing)
                gap != null -> Triple("close_gap", gap.skill, gap.name)
                else -> Triple("keep_going", null, null)
            }
        }

        private fun weight(level: Level): Double = if (level == Level.Gap) 1.0 else HALF

        override fun toString(): String = "ShowHome(goals=$goals)"

        private companion object {
            const val HALF = 0.5
        }
    }
}
