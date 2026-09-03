package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

class SecondFactorMethodRegistrySpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

        "finds a registered method by its own kind" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP)
            val registry = SecondFactorMethodRegistry.Default(listOf(totp))

            registry.find(SecondFactorKind.TOTP) shouldBe totp
        }

        "answers null for a kind nothing registered" {
            val registry = SecondFactorMethodRegistry.Default(emptyList())

            registry.find(SecondFactorKind.TOTP).shouldBeNull()
        }

        "an empty registry answers no enrolled methods for anyone — no real method exists yet" {
            val registry = SecondFactorMethodRegistry.Default(emptyList())

            registry.enrolledFor(userId) shouldBe emptySet()
        }

        "collects every registered method the user is actually enrolled in, not merely registered" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val registry = SecondFactorMethodRegistry.Default(listOf(totp))

            registry.enrolledFor(userId) shouldBe setOf(SecondFactorKind.TOTP)
        }

        "a registered method nobody enrolled in contributes nothing" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP)
            val registry = SecondFactorMethodRegistry.Default(listOf(totp))

            registry.enrolledFor(userId) shouldBe emptySet()
        }
    })
