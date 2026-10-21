package tallyvane.knowledge.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.LocalDate

private val today = LocalDate.of(2026, 10, 21)

private class Ranked : Portfolio.Report {
    val roles = mutableListOf<Triple<String, String?, Double>>()
    val projects = mutableListOf<Pair<String?, Double>>()
    val bullets = mutableListOf<Pair<String, Double>>()

    override fun role(company: String, title: String?, period: RolePeriod, score: Double) {
        roles += Triple(company, title, score)
    }

    override fun project(title: String?, score: Double) {
        projects += title to score
    }

    override fun bullet(bullet: Bullet, score: Double) {
        bullets += bullet.text() to score
    }
}

private fun entry(id: ActionId, weights: Map<String, Double>, metric: Boolean = false) =
    ActionEntry(id, weights.mapKeys { slug(it.key) }, metric)

private fun rank(portfolio: Portfolio, requirements: List<Requirement>, onlyWithBullets: Boolean = false): Ranked =
    Ranked().also { portfolio.rankTo(it, requirements, today, onlyWithBullets) }

private fun bulletFor(text: String, ids: List<ActionId>) = Bullet(text, ids)

class PortfolioSpec :
    StringSpec(
        {
            val caching = Requirement(slug("caching"), importance = 3, must = true)
            val redis = Requirement(slug("redis"), importance = 2, must = true)
            val current = RolePeriod("2022", null, true)

            fun role(period: RolePeriod = current, company: String = "Acme", vararg projects: ProjectEntry) =
                RoleEntry(company, "Engineer", period, projects.toList())

            "an action scores the sum of weight x importance over the requirements" {
                val a = actionId()
                val project = ProjectEntry("P", emptyList(), listOf(entry(a, mapOf("caching" to 0.8, "redis" to 1.0))))
                val ranked = rank(Portfolio(listOf(role(projects = arrayOf(project)))), listOf(caching, redis))
                ranked.projects.single().second shouldBe (0.8 * 3 + 1.0 * 2).plusOrMinus(1e-9)
            }

            "an action with no weight for any requirement scores nothing" {
                val project = ProjectEntry("P", emptyList(), listOf(entry(actionId(), mapOf("kafka" to 1.0))))
                rank(Portfolio(listOf(role(projects = arrayOf(project)))), listOf(caching)).projects.single().second shouldBe 0.0
            }

            "a result with a metric adds a fifth" {
                val withMetric = ProjectEntry("M", emptyList(), listOf(entry(actionId(), mapOf("caching" to 1.0), metric = true)))
                val without = ProjectEntry("N", emptyList(), listOf(entry(actionId(), mapOf("caching" to 1.0))))
                val ranked = rank(Portfolio(listOf(role(projects = arrayOf(withMetric, without)))), listOf(caching))
                ranked.projects.toMap()["M"]!! shouldBe (3.0 * 1.2).plusOrMinus(1e-9)
                ranked.projects.toMap()["N"]!! shouldBe 3.0.plusOrMinus(1e-9)
            }

            "a project scores the sum of its actions" {
                val project =
                    ProjectEntry(
                        "P",
                        emptyList(),
                        listOf(entry(actionId(), mapOf("caching" to 0.5)), entry(actionId(), mapOf("caching" to 1.0))),
                    )
                rank(Portfolio(listOf(role(projects = arrayOf(project)))), listOf(caching)).projects.single().second shouldBe
                    (1.5 * 3).plusOrMinus(1e-9)
            }

            "a role that ended loses a tenth for each year since, and never more than six tenths" {
                RolePeriod("2019", "2024", null).recencyOn(today) shouldBe (1.0 - 0.1 * (today.toEpochDay() - LocalDate.of(2024, 12, 31).toEpochDay()) / 365.25).plusOrMinus(1e-9)
                RolePeriod("2005", "2010", false).recencyOn(today) shouldBe 0.4
                RolePeriod("2022", null, true).recencyOn(today) shouldBe 1.0
            }

            "a role that ends in the future, or with no known end, is not penalised" {
                RolePeriod("2022", "2027", null).recencyOn(today) shouldBe 1.0
                RolePeriod("2022", null, null).recencyOn(today) shouldBe 1.0
                RolePeriod(null, "soon", null).recencyOn(today) shouldBe 1.0
            }

            "an older role scores less for the same action" {
                val a = ProjectEntry("old", emptyList(), listOf(entry(actionId(), mapOf("caching" to 1.0))))
                val b = ProjectEntry("new", emptyList(), listOf(entry(actionId(), mapOf("caching" to 1.0))))
                val ranked =
                    rank(
                        Portfolio(listOf(role(RolePeriod("2005", "2010", false), "Old", a), role(current, "New", b))),
                        listOf(caching),
                    )
                ranked.projects.toMap()["old"]!! shouldBe (3 * 0.4).plusOrMinus(1e-9)
                ranked.projects.toMap()["new"]!! shouldBe 3.0.plusOrMinus(1e-9)
            }

            "roles come most recent first, the current one before all" {
                val portfolio =
                    Portfolio(
                        listOf(
                            role(RolePeriod("2015", "2018", false), "Oldest"),
                            role(RolePeriod("2022", null, true), "Now"),
                            role(RolePeriod("2018", "2021-06", false), "Middle"),
                        ),
                    )
                rank(portfolio, listOf(caching)).roles.map { it.first } shouldBe listOf("Now", "Middle", "Oldest")
            }

            "a role shows its three best projects" {
                val projects = (1..5).map { n -> ProjectEntry("p$n", emptyList(), listOf(entry(actionId(), mapOf("caching" to n / 5.0)))) }
                rank(Portfolio(listOf(role(projects = projects.toTypedArray()))), listOf(caching)).projects.map { it.first } shouldBe
                    listOf("p5", "p4", "p3")
            }

            "a résumé counts only the projects that have bullets towards the three" {
                val a = actionId()
                val closed = ProjectEntry("closed", listOf(bulletFor("Did a thing.", listOf(a))), listOf(entry(a, mapOf("caching" to 0.1))))
                val open = ProjectEntry("open", emptyList(), listOf(entry(actionId(), mapOf("caching" to 1.0))))
                val portfolio = Portfolio(listOf(role(projects = arrayOf(closed, open))))
                rank(portfolio, listOf(caching), onlyWithBullets = true).projects.map { it.first } shouldBe listOf("closed")
                rank(portfolio, listOf(caching), onlyWithBullets = false).projects.map { it.first } shouldBe listOf("open", "closed")
            }

            "bullets are ordered by the score of the actions they speak for" {
                val weak = actionId()
                val strong = actionId()
                val project =
                    ProjectEntry(
                        "P",
                        listOf(bulletFor("Weak.", listOf(weak)), bulletFor("Strong.", listOf(strong)), bulletFor("Both.", listOf(weak, strong))),
                        listOf(entry(weak, mapOf("caching" to 0.2)), entry(strong, mapOf("caching" to 0.9))),
                    )
                rank(Portfolio(listOf(role(projects = arrayOf(project)))), listOf(caching)).bullets.map { it.first } shouldBe
                    listOf("Both.", "Strong.", "Weak.")
            }

            "the gap report gives the best weight any action has for each requirement" {
                val portfolio =
                    Portfolio(
                        listOf(
                            role(
                                projects =
                                    arrayOf(
                                        ProjectEntry("P", emptyList(), listOf(entry(actionId(), mapOf("caching" to 0.9, "redis" to 0.3)), entry(actionId(), mapOf("redis" to 0.45)))),
                                    ),
                            ),
                        ),
                    )
                val seen = mutableListOf<Triple<Boolean, Double, Boolean>>()
                portfolio.gaps(listOf(caching, redis, Requirement(slug("kafka"), 2, true))).writeTo(
                    object : GapReport.Record {
                        override fun gap(requirement: Requirement, bestWeight: Double, missing: Boolean) {
                            seen += Triple(requirement.isMust(), bestWeight, missing)
                        }
                    },
                )
                seen shouldBe listOf(Triple(true, 0.9, false), Triple(true, 0.45, true), Triple(true, 0.0, true))
            }

            "a requirement that is not a must is never missing" {
                val portfolio = Portfolio(listOf(role()))
                val seen = mutableListOf<Boolean>()
                portfolio.gaps(listOf(Requirement(slug("kafka"), 1, must = false))).writeTo(
                    object : GapReport.Record {
                        override fun gap(requirement: Requirement, bestWeight: Double, missing: Boolean) {
                            seen += missing
                        }
                    },
                )
                seen shouldBe listOf(false)
            }

            "a weight of exactly one half is not missing" {
                val portfolio = Portfolio(listOf(role(projects = arrayOf(ProjectEntry("P", emptyList(), listOf(entry(actionId(), mapOf("caching" to 0.5))))))))
                val seen = mutableListOf<Boolean>()
                portfolio.gaps(listOf(caching)).writeTo(
                    object : GapReport.Record {
                        override fun gap(requirement: Requirement, bestWeight: Double, missing: Boolean) {
                            seen += missing
                        }
                    },
                )
                seen shouldBe listOf(false)
            }

            "skills shown at six tenths or more are the person's strong skills, at their highest weight" {
                val portfolio =
                    Portfolio(
                        listOf(
                            role(
                                projects =
                                    arrayOf(
                                        ProjectEntry("P", emptyList(), listOf(entry(actionId(), mapOf("redis" to 1.0, "caching" to 0.59)), entry(actionId(), mapOf("caching" to 0.6, "redis" to 0.7)))),
                                    ),
                            ),
                        ),
                    )
                val strong = linkedMapOf<String, Double>()
                portfolio.strongSkillsTo(ActionEntry.StrongSkills { skill, weight -> strong[skill.value] = weight })
                strong shouldBe mapOf("redis" to 1.0, "caching" to 0.6)
            }

            "importance must be one to three" {
                io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> { Requirement(slug("x"), 0, true) }
                io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> { Requirement(slug("x"), 4, true) }
            }

            "a vacancy knows which skills it asks for" {
                val vacancy = Vacancy("Senior", listOf(caching), listOf("Kafka"))
                vacancy.asksFor(slug("caching")) shouldBe true
                vacancy.asksFor(slug("redis")) shouldBe false
            }
        },
    )
