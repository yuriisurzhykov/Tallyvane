package tallyvane.sessions.web

import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.DeviceOutcome

/**
 * What each way an act on a person's devices can fail means over HTTP.
 *
 * Each route here is closed to strangers at the edge, so the first two are what a session that ended
 * between the edge's look and this route's own gets.
 */
internal class DeviceProblems : Problems<DeviceOutcome.Failed> {
    override fun Answers.of(failure: DeviceOutcome.Failed): Problem = when (failure) {
        is DeviceOutcome.Failed.SignInRequired -> signInRequired()
        is DeviceOutcome.Failed.SessionExpired -> sessionExpired()
        is DeviceOutcome.Failed.NoSuchDevice -> missing("There is no such device.")
        is DeviceOutcome.Failed.NameRefused -> invalid(listOf(FieldError("name", "name-invalid")))
    }

    override fun toString(): String = "DeviceProblems"
}
