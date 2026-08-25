package tallyvane.resume.application

import tallyvane.contacts.contract.Person

class PromptComposer {
    fun compose(person: Person): String = person.toString()
}
