package tallyvane.authentication.application

import tallyvane.authentication.application.port.SeedSource

/**
 * [SeedSourceFake] held to the suite every [SeedSource] must pass.
 */
class SeedSourceFakeSpec : SeedSourceConformance() {
    override fun fresh(): SeedSource = SeedSourceFake()
}
