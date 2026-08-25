package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * The shapes `usecase-is-interface` has to reject that its fixture does not cover.
 *
 * A single dirty fixture proves a rule fires once. It says nothing about the ways
 * round it, and the ways round a rule written against a marker are predictable:
 * reach the marker indirectly, or arrive as a declaration kind the predicate does
 * not ask for.
 */
class UseCaseCornerSpec :
    StringSpec(
        {
            val corners = { fixtureScope("usecase-corners") }

            "rejects a top-level class that reaches the marker through its use-case interface" {
                val violations = usecaseIsInterface(corners())

                violations.joinToString() shouldContain "TopLevelViaInterface.kt"
            }

            "rejects an object implementing the marker, which is not a class" {
                val violations = usecaseIsInterface(corners())

                violations.joinToString() shouldContain "ObjectUseCase.kt"
            }

            "flags every wrong shape here and nothing that is allowed" {
                val flagged = usecaseIsInterface(corners()).joinToString()

                flagged shouldContain "TopLevelViaInterface.kt"
                flagged shouldContain "ObjectUseCase.kt"
                flagged shouldContain "ContractObject.kt"
                flagged shouldNotContain "LocalVerdict.kt"
            }

            "rejects a private helper on the interface, which Kotlin does compile" {
                singlePublicMethod(corners()).joinToString() shouldContain "PrivateHelper.kt"
            }

            "rejects a default implementation, which a count alone would have accepted" {
                singlePublicMethod(corners()).joinToString() shouldContain "DefaultBody.kt"
            }

            "does not mistake a local of type Verdict for a declared signature" {
                noVerdictInSignature(corners()).joinToString() shouldNotContain "LocalVerdict.kt"
            }

            "rejects a use case published in a contract, even as an object" {
                contractNoLogic(corners()).joinToString() shouldContain "ContractObject.kt"
            }
        },
    )
