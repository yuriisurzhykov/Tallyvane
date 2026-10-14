package tallyvane.authentication.web

import tallyvane.authentication.application.Fault
import tallyvane.platform.http.FieldError

/**
 * What an administrator is told is wrong with what they sent: the field and the code of each fault for a form to
 * point at, and the words of all of them for a person to read.
 *
 * One instance hears the faults of one answer.
 */
internal class FaultFields : Fault.Record {
    private val errors = mutableListOf<FieldError>()
    private val said = mutableListOf<String>()

    /**
     * Hears every fault of [write], a failure telling its faults, and gives back what it heard.
     */
    fun hearing(write: (Fault.Record) -> Unit): FaultFields {
        write(this)
        return this
    }

    fun errors(): List<FieldError> = errors.toList()

    fun detail(): String = said.joinToString(separator = " ")

    override fun fault(field: String, code: String, said: String) {
        errors += FieldError(field, code)
        this.said += said
    }

    override fun toString(): String = "FaultFields(${errors.size})"
}
