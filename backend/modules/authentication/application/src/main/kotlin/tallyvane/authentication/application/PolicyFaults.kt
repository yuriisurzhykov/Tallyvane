package tallyvane.authentication.application

import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Violation
import kotlin.time.Duration

/**
 * Says each bound a draft broke as a [Fault], with the numbers the code sets.
 *
 * Durations are said in milliseconds, as the API takes them.
 */
internal class PolicyFaults(private val words: PolicyWords) : Violation.Report<Fault> {
    override fun attemptLifetimeOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): Fault = Fault(
        "attempt_lifetime_millis",
        "out-of-bounds",
        "An attempt may stay open for ${allowed.start.inWholeMilliseconds} to " +
            "${allowed.endInclusive.inWholeMilliseconds} milliseconds, not ${given.inWholeMilliseconds}.",
    )

    override fun maxFailuresOutOfBounds(given: Int, allowed: IntRange): Fault = Fault(
        "max_failures",
        "out-of-bounds",
        "An attempt may end after ${allowed.first} to ${allowed.last} wrong answers, not $given.",
    )

    override fun firstDelayOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): Fault = Fault(
        "first_delay_millis",
        "out-of-bounds",
        "The first pause may be ${allowed.start.inWholeMilliseconds} to " +
            "${allowed.endInclusive.inWholeMilliseconds} milliseconds, not ${given.inWholeMilliseconds}.",
    )

    override fun emptyStep(position: Int): Fault = Fault(
        "steps[${position - 1}].accepts",
        "empty",
        "Step $position accepts no factor, so nobody could pass it.",
    )

    override fun nothingIdentifiesTheAccount(): Fault = Fault(
        "steps",
        "nothing-identifies-the-account",
        "The policy needs a step that applies to everyone and needs no setup, such as Google; " +
            "without one there is no way to learn whose account is signing in.",
    )

    override fun belowTheFloor(purpose: Purpose): Fault = Fault(
        "steps",
        "below-the-floor",
        "The policy for ${words.wordOf(purpose)} must keep the second factor it always demanded.",
    )

    override fun wouldLockPeopleOut(purpose: Purpose): Fault = Fault(
        "steps",
        "would-lock-people-out",
        "The policy for ${words.wordOf(purpose)} would demand a second factor from everyone, and a person who has " +
            "not set one up could not sign in to set it up. Ask for it only from people who have set it up.",
    )
}
