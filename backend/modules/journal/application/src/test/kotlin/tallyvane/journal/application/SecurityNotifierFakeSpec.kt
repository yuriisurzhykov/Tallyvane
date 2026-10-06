package tallyvane.journal.application

import tallyvane.journal.application.port.SecurityNotifier

/**
 * The fake, held to the suite the adapters pass.
 */
class SecurityNotifierFakeSpec : SecurityNotifierConformance() {
    override fun fresh(): SecurityNotifier = SecurityNotifierFake()
}
