package tallyvane.career.application

import tallyvane.career.application.port.Goals
import kotlin.uuid.Uuid

class GoalsFake : Goals {
    private val chosen = mutableMapOf<Uuid, String>()

    override fun of(account: Uuid): String? = chosen[account]

    override fun set(account: Uuid, role: String) {
        chosen[account] = role
    }

    override fun toString(): String = "GoalsFake(${chosen.size})"
}
