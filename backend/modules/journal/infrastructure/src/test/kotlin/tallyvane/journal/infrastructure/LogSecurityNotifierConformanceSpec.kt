package tallyvane.journal.infrastructure

import org.slf4j.helpers.NOPLogger
import tallyvane.journal.application.SecurityNotifierConformance
import tallyvane.journal.application.port.SecurityNotifier

/**
 * The log adapter, held to the suite every notifier passes.
 */
class LogSecurityNotifierConformanceSpec : SecurityNotifierConformance() {
    override fun fresh(): SecurityNotifier = LogSecurityNotifier(NOPLogger.NOP_LOGGER)
}
