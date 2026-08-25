package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * The shape `platform:kernel`'s `UseCase` documents, and `_template/README.md`
 * tells the next author to copy, checked against the rules that police it.
 *
 * `ArchitectureRulesSpec` proves each rule fires on a wrong shape and that the
 * production tree is clean — but the tree holds no use case yet, so "clean" there
 * says nothing about whether these rules accept the right shape. A rule that has
 * gone blind and a rule that is satisfied look identical from the outside, which
 * is why the first assertion here is that the fixture was recognised at all.
 */
class UseCaseShapeSpec :
    StringSpec(
        {
            val canonical = { fixtureScope("usecase-canonical") }

            "the canonical shape is recognised as a use case, so the rest of this spec means something" {
                canonical().useCaseInterfaces().map { it.name } shouldBe listOf("SignInUseCase")
                canonical().useCaseImplementations().map { it.name } shouldBe listOf("SignIn")
            }

            "an interface with the implementation nested inside it satisfies usecase-is-interface" {
                usecaseIsInterface(canonical()).shouldBeEmpty()
            }

            "one method named for the action satisfies single-public-method" {
                singlePublicMethod(canonical()).shouldBeEmpty()
            }

            "a spec named after the implementation satisfies usecase-has-test" {
                usecaseHasTest(canonical()).shouldBeEmpty()
            }

            "a use case in application, not contract, satisfies contract-no-logic" {
                contractNoLogic(canonical()).shouldBeEmpty()
            }

            "a transactional block returning Verdict satisfies no-verdict-in-signature" {
                noVerdictInSignature(canonical()).shouldBeEmpty()
            }
        },
    )
