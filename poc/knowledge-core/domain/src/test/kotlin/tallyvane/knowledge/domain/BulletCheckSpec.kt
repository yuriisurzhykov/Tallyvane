package tallyvane.knowledge.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BulletCheckSpec :
    StringSpec(
        {
            val check = BulletCheck(dictionary)
            val redis = Technology("Redis")
            val project =
                facts(
                    company = "Acme",
                    actions =
                        listOf(
                            actionFacts(
                                action(kind = ActionKind.INVESTIGATED, verb = "profiled", what = "queries", finding = "about 70% were repeated reads"),
                                listOf(redis),
                            ),
                        ),
                    collaborators = listOf(Collaborator("DBA")),
                    outcomes = listOf(outcome()),
                )

            fun verdict(text: String) = check.verify(text, project)

            "a bullet made of the facts passes" {
                verdict("Cut database load by about 40% by designing a Redis cache agreed with the DBA.")
                    .isAcceptable() shouldBe true
            }

            "a figure the person gave as a result may be used" {
                verdict("Reduced load by 40% after profiling queries.").isAcceptable() shouldBe true
            }

            "a figure the person gave as a finding may be used" {
                verdict("Found that 70% of reads were repeats.").isAcceptable() shouldBe true
            }

            "a figure the person did not give is refused" {
                val rejected = verdict("Cut database load by 60% with a Redis cache.")
                rejected.isAcceptable() shouldBe false
                rejected.why() shouldContain "60"
            }

            "a technology that is not in the facts is refused even when it is a known one" {
                val rejected = verdict("Built a cache with Redis and Kafka.")
                rejected.isAcceptable() shouldBe false
                rejected.why() shouldContain "Kafka"
            }

            "a name that is nowhere is refused" {
                val rejected = verdict("Built a Redis cache for the Orion platform.")
                rejected.isAcceptable() shouldBe false
                rejected.why() shouldContain "Orion"
            }

            "a technology written in another form is accepted when the facts have it" {
                verdict("Built a cache on redis.").isAcceptable() shouldBe true
            }

            "the company named in the facts may be named" {
                verdict("Built a Redis cache at Acme.").isAcceptable() shouldBe true
            }

            "the first word of a sentence is not taken for a name" {
                verdict("Profiled queries. Designed a cache.").isAcceptable() shouldBe true
            }

            "generic acronyms are not names" {
                verdict("Profiled SQL queries and an API with Redis.").isAcceptable() shouldBe true
            }

            "a bullet over the word limit is refused" {
                val long = "Built " + List(40) { "cache" }.joinToString(" ") + "."
                verdict(long).isAcceptable() shouldBe false
                BulletCheck(dictionary, maxWords = 41).verify(long, project).isAcceptable() shouldBe true
            }

            "an empty bullet is refused" {
                verdict("   ").isAcceptable() shouldBe false
            }

            "without a model, the bullets are the actions with what they used and the result" {
                val builtId = actionId()
                val built = actionFacts(action(kind = ActionKind.BUILT, verb = "built", what = "a catalog cache"), listOf(redis), builtId)
                val profiled = actionFacts(action(kind = ActionKind.INVESTIGATED, verb = "profiled", what = "database queries"))
                val bullets =
                    facts(actions = listOf(profiled, built), producedOutcomes = listOf(OutcomeFacts(outcome(), listOf(builtId))))
                        .fallbackBullets()
                bullets.map { it.text() } shouldBe
                    listOf(
                        "Built a catalog cache using Redis, reducing database load by about 40%.",
                        "Profiled database queries.",
                    )
            }

            "without a model, a result goes only onto the action the person said produced it" {
                val profiledId = actionId()
                val built = actionFacts(action(kind = ActionKind.BUILT, verb = "built", what = "a catalog cache"), listOf(redis))
                val profiled =
                    actionFacts(action(kind = ActionKind.INVESTIGATED, verb = "profiled", what = "database queries"), id = profiledId)
                val project = facts(actions = listOf(profiled, built), producedOutcomes = listOf(OutcomeFacts(outcome(), listOf(profiledId))))
                project.fallbackBullets().map { it.text() } shouldBe
                    listOf(
                        "Built a catalog cache using Redis.",
                        "Profiled database queries, reducing database load by about 40%.",
                    )
            }

            "without a model, an outcome linked to no action is left out of the bullets" {
                val built = actionFacts(action(kind = ActionKind.BUILT, verb = "built", what = "a catalog cache"), listOf(redis))
                facts(actions = listOf(built), outcomes = listOf(outcome())).fallbackBullets().map { it.text() } shouldBe
                    listOf("Built a catalog cache using Redis.")
            }

            "without a model, at most two bullets and each one's own checks hold" {
                val many = (1..5).map { actionFacts(action(what = "thing $it")) }
                facts(actions = many).fallbackBullets().size shouldBe 2
                facts().fallbackBullets().size shouldBe 0
            }
        },
    )
