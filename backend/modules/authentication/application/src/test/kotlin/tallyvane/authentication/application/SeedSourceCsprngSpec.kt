package tallyvane.authentication.application

import tallyvane.authentication.application.port.SeedSource

/**
 * [SeedSourceCsprng] held to the suite every [SeedSource] must pass.
 */
class SeedSourceCsprngSpec : SeedSourceConformance() {
    override fun fresh(): SeedSource = SeedSource.Csprng()
}
