package tallyvane.authentication.application

import tallyvane.authentication.application.port.AccountFailures
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.AccountGuesses
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.CodeVerdict
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Progress
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.SpendVerdict
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.authentication.domain.VerifiedFactor
import tallyvane.identity.contract.AccountId
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A person answers the second step of a sign-in or of a confirmation with a code (ADR-082, ADR-093).
 *
 * The attempt is found by the browser's secret and must be waiting for this kind of answer. A right
 * answer is recorded in the attempt as the factor it proves; a wrong one is recorded in the attempt and,
 * for a code from the authenticator, in the account's own count, in a transaction that commits although
 * the answer is a refusal. A recovery code is spent, and spending one retires the TOTP seed, so a lost
 * phone stops mattering at once. The journal is told when a recovery code is spent and when an attempt is
 * closed by its wrong answers (ADR-095).
 */
public interface VerifySecondFactorUseCase : UseCase {
    /**
     * @param attempt The secret from the browser's `__Host-attempt` cookie, or null when it sent none.
     */
    public suspend fun verify(attempt: Secret?, submission: Submission): Verification

    public class VerifySecondFactor(
        private val attempts: Attempts,
        private val policies: ActivePolicies,
        private val owners: AttemptOwners,
        private val totp: TotpEnrollments,
        private val codes: RecoveryCodeSets,
        private val failures: AccountFailures,
        private val words: RecoveryCodeWords,
        private val digests: Digests,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SignInKeys,
        private val journal: SecurityJournal,
    ) : VerifySecondFactorUseCase {
        override suspend fun verify(attempt: Secret?, submission: Submission): Verification {
            val key = attempt?.let(keys::keyOf) ?: return Verification.Failed.Closed()
            return transactions.inTransaction {
                Sitting(key, clock.now()).answer(submission).decide<Verdict<Verification>>(
                    commit = { Verdict.Commit(it) },
                    rollback = { Verdict.Rollback(it) },
                )
            }
        }

        /**
         * One answer, judged inside the transaction that records what came of it.
         */
        private inner class Sitting(private val key: Digest, private val now: Instant) {
            fun answer(submission: Submission): Ruling {
                val attempt = attempts.find(key)
                val purpose = attempt?.let { found -> PURPOSES.firstOrNull(found::isFor) }
                val account = attempt?.let(owners::of)
                if (attempt == null || purpose == null || account == null) {
                    return closed()
                }
                val kind = submission.reportTo(KindOf())
                return when (val standing = policies.progressOf(attempt, purpose, now).reportTo(Waiting(kind))) {
                    is Standing.Closed -> closed()
                    is Standing.NotWanted -> undo(Verification.Failed.NotWanted())
                    is Standing.Paused -> undo(Verification.Failed.Paused(standing.wait(now)))
                    is Standing.Open -> submission.reportTo(Answering(attempt, purpose, account, kind))
                }
            }

            private fun closed(): Ruling = undo(Verification.Failed.Closed())

            /**
             * What to do with each kind of answer, for an attempt that is waiting for it.
             */
            private inner class Answering(
                private val attempt: Attempt,
                private val purpose: Purpose,
                private val account: AccountId,
                private val kind: FactorKind,
            ) : Submission.Report<Ruling> {
                override fun totp(code: String): Ruling {
                    val enrollment = totp.lock(account)
                    val pause = accountPause()
                    return when {
                        pause > Duration.ZERO -> undo(Verification.Failed.Paused(pause))
                        enrollment == null -> wrong(countedAgainstAccount = true)
                        else -> enrollment.check(code, now).reportTo(TotpChecked())
                    }
                }

                override fun recovery(code: String): Ruling {
                    val enrollment = totp.lock(account)
                    val digest = digests.of(words.normalised(code))
                    val spent = codes.of(account)?.spend(digest, now)
                    return spent?.reportTo(Spending(enrollment)) ?: wrong(countedAgainstAccount = false)
                }

                private fun accountPause(): Duration = failures.recent(account, now - AccountGuesses.WINDOW)
                    .pauseLeft(now, policies.firstDelayOf(purpose))

                private fun proved(kind: FactorKind, left: Int?): Ruling =
                    when (attempts.save(key, attempt.withVerified(VerifiedFactor.confirming(kind, now)))) {
                        AttemptSaveOutcome.Saved -> keep(Verification.Verified(left))
                        AttemptSaveOutcome.Superseded -> undo(Verification.Failed.Busy())
                    }

                /**
                 * Records a wrong answer and commits it with the refusal. The account's own count is kept
                 * even when another request changed the attempt first, so racing requests do not buy guesses.
                 */
                private fun wrong(countedAgainstAccount: Boolean): Ruling {
                    if (countedAgainstAccount) {
                        failures.record(account, now)
                    }
                    val later = attempt.withFailure(now)
                    if (attempts.save(key, later) == AttemptSaveOutcome.Superseded) {
                        return keep(Verification.Failed.Busy())
                    }
                    val standing = policies.progressOf(later, purpose, now).reportTo(Waiting(kind))
                    val wait = maxOf(standing.waitAfterWrong(now), accountPause())
                    return if (standing is Standing.Closed) {
                        stopped()
                    } else {
                        keep(Verification.Failed.WrongCode(wait.takeIf { it > Duration.ZERO }))
                    }
                }

                /**
                 * The wrong answer that closed the attempt: the journal is told, so the person can see that
                 * someone got past Google and failed the code.
                 */
                private fun stopped(): Ruling {
                    journal.guessingStopped(account)
                    return keep(Verification.Failed.Closed())
                }

                private inner class TotpChecked : CodeVerdict.Report<Ruling> {
                    override fun accepted(next: TotpEnrollment): Ruling {
                        totp.keep(account, next)
                        return proved(FactorKind.Totp, null)
                    }

                    override fun wrong(): Ruling = wrong(countedAgainstAccount = true)
                }

                private inner class Spending(private val enrollment: TotpEnrollment?) :
                    SpendVerdict.Report<Ruling> {
                    override fun spent(next: RecoveryCodes): Ruling {
                        codes.keep(account, next)
                        journal.recoveryCodeSpent(account, next.remaining())
                        enrollment?.let { totp.keep(account, it.retired()) }
                        return proved(FactorKind.RecoveryCode, next.remaining())
                    }

                    override fun unknown(): Ruling = wrong(countedAgainstAccount = false)
                }
            }
        }

        private fun keep(verification: Verification): Ruling = Ruling(verification, commits = true)

        private fun undo(verification: Verification): Ruling = Ruling(verification, commits = false)

        /**
         * What an answer came to, and whether what was recorded while judging it stays.
         *
         * A wrong answer stays, so that the limit on guessing cannot be beaten by a client that makes every
         * request fail. A right answer that lost a race does not, or the code it used would be spent for
         * nothing.
         */
        private class Ruling(private val verification: Verification, private val commits: Boolean) {
            fun <T> decide(commit: (Verification) -> T, rollback: (Verification) -> T): T =
                if (commits) commit(verification) else rollback(verification)
        }

        /**
         * Where the attempt stands for an answer of one kind.
         */
        private sealed interface Standing {
            fun waitAfterWrong(now: Instant): Duration = Duration.ZERO

            class Open : Standing

            class Paused(private val until: Instant) : Standing {
                fun wait(now: Instant): Duration = until - now

                override fun waitAfterWrong(now: Instant): Duration = wait(now)
            }

            class Closed : Standing

            class NotWanted : Standing
        }

        private class Waiting(private val kind: FactorKind) : Progress.Report<Standing> {
            override fun awaiting(accepted: Set<FactorKind>): Standing =
                if (kind in accepted) Standing.Open() else Standing.NotWanted()

            override fun paused(accepted: Set<FactorKind>, until: Instant): Standing =
                if (kind in accepted) Standing.Paused(until) else Standing.NotWanted()

            override fun exhausted(): Standing = Standing.Closed()

            override fun expired(): Standing = Standing.Closed()

            override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): Standing =
                Standing.NotWanted()

            override fun restricted(
                factors: Set<FactorKind>,
                authenticatedAt: Instant,
                subject: String,
                toSetUp: Set<FactorKind>,
            ): Standing = Standing.NotWanted()
        }

        private class KindOf : Submission.Report<FactorKind> {
            override fun totp(code: String): FactorKind = FactorKind.Totp

            override fun recovery(code: String): FactorKind = FactorKind.RecoveryCode
        }

        private companion object {
            /**
             * The purposes that end in a second step: signing in, and confirming a dangerous act.
             */
            val PURPOSES = listOf(Purpose.Login, Purpose.AdminLogin, Purpose.StepUp)
        }
    }
}
