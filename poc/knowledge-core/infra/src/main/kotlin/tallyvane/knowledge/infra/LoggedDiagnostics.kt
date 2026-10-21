package tallyvane.knowledge.infra

import org.slf4j.LoggerFactory
import tallyvane.knowledge.domain.port.Diagnostics

/** What is dropped from a model's reply, or not understood, goes to the log, so it is seen and not lost silently. */
class LoggedDiagnostics : Diagnostics {
    private val log = LoggerFactory.getLogger("knowledge-core.dropped")

    override fun dropped(entity: String, reason: String) = log.warn("Dropped {}: {}", entity, reason)

    override fun unmapped(kind: String, value: String) = log.warn("Unmapped {}: {}", kind, value)
}
