package tallyvane.catalog.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.catalog.contract.RoleTrack
import tallyvane.catalog.contract.SkillName
import tallyvane.catalog.contract.Station

private val KOTLIN = SkillName("kotlin", "Kotlin")

private fun role(vararg stations: Station): RoleTrack = RoleTrack("staff-android", "Staff Android", stations.toList())

class CatalogSpec :
    StringSpec(
        {
            "finds a role by its slug, and nothing for a slug it does not have" {
                val catalog = Catalog(listOf(KOTLIN), listOf(role(Station("kotlin", 1.0))))

                catalog.role("staff-android")?.name shouldBe "Staff Android"
                catalog.role("cto") shouldBe null
            }

            "refuses a station whose skill it does not list" {
                shouldThrow<IllegalArgumentException> { Catalog(listOf(KOTLIN), listOf(role(Station("swift", 1.0)))) }
            }

            "refuses an importance of zero or above one, a role with no stations and a skill listed twice" {
                shouldThrow<IllegalArgumentException> { Catalog(listOf(KOTLIN), listOf(role(Station("kotlin", 0.0)))) }
                shouldThrow<IllegalArgumentException> { Catalog(listOf(KOTLIN), listOf(role(Station("kotlin", 1.1)))) }
                shouldThrow<IllegalArgumentException> { Catalog(listOf(KOTLIN), listOf(role())) }
                shouldThrow<IllegalArgumentException> { Catalog(listOf(KOTLIN, KOTLIN), emptyList()) }
            }

            "refuses a slug that is not lowercase words joined by dashes" {
                shouldThrow<IllegalArgumentException> { Catalog(listOf(SkillName("System Design", "x")), emptyList()) }
            }
        },
    )
