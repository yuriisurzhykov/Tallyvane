package tallyvane.knowledge.application

import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.fixtures.DemoScenario
import tallyvane.knowledge.fixtures.DemoScript
import tallyvane.knowledge.fixtures.DemoWorld

/** The demo of the specification, from the first question to the résumé, against the fakes and without a network. */
class DemoScenarioSpec :
    StringSpec(
        {
            "the demo runs from the first question to a résumé in which every bullet has the person's words behind it" {
                runBlocking {
                    val llm = DemoScript.llm()
                    val world = DemoWorld(llm)
                    DemoScenario.run(world.core, world.store, llm) { world.store.rawEntries() }
                }
            }
        },
    )
