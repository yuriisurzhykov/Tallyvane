package tallyvane.knowledge.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun nameOf(technology: Technology): String = technology.toString()

class NamesSpec :
    StringSpec(
        {
            "a technology is found by its canonical name in any case" {
                nameOf(dictionary.resolve("redis")) shouldBe "Redis"
                nameOf(dictionary.resolve("  REDIS ")) shouldBe "Redis"
            }

            "a technology is found by a Cyrillic alias" {
                nameOf(dictionary.resolve("редис")) shouldBe "Redis"
                nameOf(dictionary.resolve("Постгрес")) shouldBe "PostgreSQL"
            }

            "a technology is found by a Latin alias" {
                nameOf(dictionary.resolve("Postgres")) shouldBe "PostgreSQL"
                nameOf(dictionary.resolve("Apache Kafka")) shouldBe "Kafka"
            }

            "a technology that is not in the dictionary is kept as the person wrote it" {
                nameOf(dictionary.resolve("  Memcached ")) shouldBe "Memcached"
            }

            "the dictionary recognises names and aliases but not other words" {
                dictionary.recognises("Redis") shouldBe true
                dictionary.recognises("редис") shouldBe true
                dictionary.recognises("Acme") shouldBe false
            }

            "technologies with the same name in a different case are one" {
                (Technology("Redis") == Technology("redis")) shouldBe true
                Technology("Redis").hashCode() shouldBe Technology("REDIS").hashCode()
            }

            "collaborators are kept by their role in lower case" {
                (Collaborator("DBA") == Collaborator(" dba ")) shouldBe true
                nameOf2(Collaborator("DBA")) shouldBe "dba"
            }

            "two spellings of one company share a normalised form" {
                fun normalised(name: String): String {
                    var found = ""
                    CompanyName(name).writeTo(
                        object : CompanyName.Record {
                            override fun company(name: String, normalised: String) {
                                found = normalised
                            }
                        },
                    )
                    return found
                }
                normalised("Acme") shouldBe normalised("  acme ")
                normalised("Acme  Labs") shouldBe "acme labs"
            }
        },
    )

private fun nameOf2(collaborator: Collaborator): String = collaborator.toString()
