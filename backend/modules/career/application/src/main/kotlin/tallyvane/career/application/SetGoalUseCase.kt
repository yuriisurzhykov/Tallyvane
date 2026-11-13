package tallyvane.career.application

import tallyvane.career.application.port.Goals
import tallyvane.catalog.contract.SkillCatalog
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * A person chooses the role they are heading for, from the catalog.
 */
public interface SetGoalUseCase : UseCase {
    public suspend fun set(account: Uuid, role: String): GoalOutcome

    public class SetGoal(
        private val goals: Goals,
        private val catalog: SkillCatalog,
        private val transactions: TransactionRunner,
    ) : SetGoalUseCase {
        override suspend fun set(account: Uuid, role: String): GoalOutcome {
            val track = catalog.role(role) ?: return GoalOutcome.Failed.UnknownRole()
            return transactions.inTransaction {
                goals.set(account, track.slug)
                Verdict.Commit(GoalOutcome.Set(track.slug))
            }
        }

        override fun toString(): String = "SetGoal(goals=$goals)"
    }
}
