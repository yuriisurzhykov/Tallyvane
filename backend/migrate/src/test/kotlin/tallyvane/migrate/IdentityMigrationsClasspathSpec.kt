package tallyvane.migrate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe

class IdentityMigrationsClasspathSpec : FunSpec({
    test("the migration command runtime includes the identity schema migration") {
        Thread.currentThread().contextClassLoader
            .getResource("db/migration/identity/V20260902000000__identity_schema.sql") shouldNotBe null
    }
})
